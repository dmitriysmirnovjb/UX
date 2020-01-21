// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl.text;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EmptyEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.KeyedFactoryEPBean;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author peter
 */
public class EditorHighlighterUpdater {
  @NotNull protected final Project myProject;
  @NotNull private final EditorEx myEditor;
  @Nullable private final VirtualFile myFile;

  public EditorHighlighterUpdater(@NotNull Project project, @NotNull Disposable parentDisposable, @NotNull EditorEx editor, @Nullable VirtualFile file) {
    myProject = project;
    myEditor = editor;
    myFile = file;
    MessageBusConnection myConnection = project.getMessageBus().connect(parentDisposable);
    myConnection.subscribe(FileTypeManager.TOPIC, new MyFileTypeListener());
    myConnection.subscribe(DumbService.DUMB_MODE, new DumbService.DumbModeListener() {
      @Override
      public void enteredDumbMode() {
        updateHighlighters();
      }

      @Override
      public void exitDumbMode() {
        updateHighlighters();
      }
    });

    updateHighlightersOnExtensionsChange(parentDisposable, LanguageSyntaxHighlighters.EP_NAME);
    updateHighlightersOnExtensionsChange(parentDisposable, SyntaxHighlighterLanguageFactory.EP_NAME);
    updateHighlightersOnExtensionsChange(parentDisposable, FileTypeEditorHighlighterProviders.EP_NAME);

    SyntaxHighlighter.EP_NAME.addExtensionPointListener(new ExtensionPointListener<KeyedFactoryEPBean>() {
      @Override
      public void extensionAdded(@NotNull KeyedFactoryEPBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkUpdateHighlighters(extension.key, false);
      }

      @Override
      public void extensionRemoved(@NotNull KeyedFactoryEPBean extension, @NotNull PluginDescriptor pluginDescriptor) {
        checkUpdateHighlighters(extension.key, true);
      }
    }, parentDisposable);
  }

  private <T> void updateHighlightersOnExtensionsChange(@NotNull Disposable parentDisposable, @NotNull ExtensionPointName<KeyedLazyInstance<T>> epName) {
    epName.addExtensionPointListener(
      new ExtensionPointListener<KeyedLazyInstance<T>>() {
        @Override
        public void extensionAdded(@NotNull KeyedLazyInstance<T> extension, @NotNull PluginDescriptor pluginDescriptor) {
          checkUpdateHighlighters(extension.getKey(), false);
        }

        @Override
        public void extensionRemoved(@NotNull KeyedLazyInstance<T> extension, @NotNull PluginDescriptor pluginDescriptor) {
          checkUpdateHighlighters(extension.getKey(), true);
        }
      }, parentDisposable);
  }

  private void checkUpdateHighlighters(String key, boolean updateSynchronously) {
    if (myFile != null) {
      FileType fileType = myFile.getFileType();
      boolean needUpdate = (fileType.getName().equals(key) ||
                            (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage().getID().equals(key)));
      if (!needUpdate) return;
    }

    if (ApplicationManager.getApplication().isDispatchThread() && updateSynchronously) {
      updateHighlightersSynchronously();
    }
    else {
      updateHighlighters();
    }
  }

  public void updateHighlightersAsync() {
    ReadAction
      .nonBlocking(() -> createHighlighter())
      .expireWith(myProject)
      .expireWhen(() -> (myFile != null && !myFile.isValid()) || myEditor.isDisposed())
      .coalesceBy(EditorHighlighterUpdater.class, myEditor)
      .finishOnUiThread(ModalityState.any(), highlighter -> myEditor.setHighlighter(highlighter))
      .submit(NonUrgentExecutor.getInstance());
  }

  @NotNull
  protected EditorHighlighter createHighlighter() {
    EditorHighlighter highlighter = myFile != null
                                    ? EditorHighlighterFactory.getInstance().createEditorHighlighter(myProject, myFile)
                                    : new EmptyEditorHighlighter(EditorColorsManager.getInstance().getGlobalScheme().getAttributes(HighlighterColors.TEXT));
    highlighter.setText(myEditor.getDocument().getImmutableCharSequence());
    return highlighter;
  }

  /**
   * Updates editors' highlighters. This should be done when the opened file
   * changes its file type.
   */
  public void updateHighlighters() {
    if (!myProject.isDisposed() && !myEditor.isDisposed()) {
      updateHighlightersAsync();
    }
  }

  private void updateHighlightersSynchronously() {
    if (!myProject.isDisposed() && !myEditor.isDisposed()) {
      myEditor.setHighlighter(createHighlighter());
    }
  }

  @TestOnly
  public static void completeAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

  /**
   * Listen changes of file types. When type of the file changes we need
   * to also change highlighter.
   */
  private final class MyFileTypeListener implements FileTypeListener {
    @Override
    public void fileTypesChanged(@NotNull final FileTypeEvent event) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    // File can be invalid after file type changing. The editor should be removed
      // by the FileEditorManager if it's invalid.
      FileType type = event.getRemovedFileType();
      if (type != null && !(type instanceof AbstractFileType)) {
        // Plugin is being unloaded, so we need to release plugin classes immediately
        updateHighlightersSynchronously();
      }
      else {
        updateHighlighters();
      }
    }
  }

}
