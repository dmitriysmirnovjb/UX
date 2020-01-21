/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AutoScrollFromSourceHandler implements Disposable {
  protected final Project myProject;
  protected final Alarm myAlarm;
  private final JComponent myComponent;

  public AutoScrollFromSourceHandler(@NotNull Project project, @NotNull JComponent view) {
    this(project, view, null);
  }

  public AutoScrollFromSourceHandler(@NotNull Project project, @NotNull JComponent view, @Nullable Disposable parentDisposable) {
    myProject = project;

    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
    myComponent = view;
    myAlarm = new Alarm(this);
  }

  protected String getActionName() {
    return UIBundle.message("autoscroll.from.source.action.name");
  }

  protected String getActionDescription() {
    return UIBundle.message("autoscroll.from.source.action.description");
  }

  protected abstract boolean isAutoScrollEnabled();

  protected abstract void setAutoScrollEnabled(boolean enabled);

  protected abstract void selectElementFromEditor(@NotNull FileEditor editor);

  protected ModalityState getModalityState() {
    return ModalityState.current();
  }

  protected long getAlarmDelay() {
    return Registry.intValue("ide.autoscroll.from.source.delay", 100);
  }

  public void install() {
    final MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        selectInAlarm(event.getNewEditor());
      }
    });
    updateCurrentSelection();
  }

  private void selectInAlarm(final FileEditor editor) {
    if (editor != null && myComponent.isShowing() && isAutoScrollEnabled()) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(() -> selectElementFromEditor(editor), getAlarmDelay(), getModalityState());
    }
  }

  private void updateCurrentSelection() {
    FileEditor selectedEditor = FileEditorManager.getInstance(myProject).getSelectedEditor();
    if (selectedEditor != null) {
      ApplicationManager.getApplication().invokeLater(() -> selectInAlarm(selectedEditor), ModalityState.NON_MODAL, myProject.getDisposed());
    }
  }

  @Override
  public void dispose() {
    if (!myAlarm.isDisposed()) {
      myAlarm.cancelAllRequests();
    }
  }

  public ToggleAction createToggleAction() {
    return new AutoScrollFromSourceAction(getActionName(), getActionDescription());
  }

  private class AutoScrollFromSourceAction extends ToggleAction implements DumbAware {
    AutoScrollFromSourceAction(String actionName, String actionDescription) {
      super(actionName, actionDescription, AllIcons.General.AutoscrollFromSource);
    }

    @Override
    public boolean isSelected(@NotNull final AnActionEvent event) {
      return isAutoScrollEnabled();
    }

    @Override
    public void setSelected(@NotNull final AnActionEvent event, final boolean flag) {
      setAutoScrollEnabled(flag);
      updateCurrentSelection();
    }
  }
}

