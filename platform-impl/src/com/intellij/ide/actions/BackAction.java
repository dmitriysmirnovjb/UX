
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.ui.navigation.History;
import org.jetbrains.annotations.NotNull;

public class BackAction extends AnAction implements DumbAware {
  public BackAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    History history = e.getData(History.KEY);

    if (history != null) {
      history.back();
    }
    else if (project != null) {
      IdeDocumentHistory.getInstance(project).back();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    History history = e.getData(History.KEY);
    boolean isModalContext = e.getData(PlatformDataKeys.IS_MODAL_CONTEXT) == Boolean.TRUE;

    Presentation presentation = e.getPresentation();
    if (history != null) {
      presentation.setEnabled(history.canGoBack());
    }
    else if (project != null && !project.isDisposed()) {
      presentation.setEnabled(!isModalContext && IdeDocumentHistory.getInstance(project).isBackAvailable());
    }
    else {
      presentation.setEnabled(false);
    }
  }
}