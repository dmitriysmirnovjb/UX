// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RecentProjectsGroup extends ActionGroup implements DumbAware {
  public RecentProjectsGroup() {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(ActionsBundle.message(SystemInfo.isMac ? "group.reopen.mac.text" : "group.reopen.win.text"));
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return removeCurrentProject(e == null ? null : e.getProject(), RecentProjectListActionProvider.getInstance().getActions(true));
  }

  public static AnAction[] removeCurrentProject(Project project, @NotNull List<AnAction> actions) {
    if (project == null) {
      return actions.toArray(EMPTY_ARRAY);
    }

    List<AnAction> list = new ArrayList<>();
    for (AnAction action : actions) {
      if (!(action instanceof ReopenProjectAction) || !StringUtil.equals(((ReopenProjectAction)action).getProjectPath(), project.getBasePath())) {
        list.add(action);
      }
    }
    return list.toArray(EMPTY_ARRAY);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(RecentProjectListActionProvider.getInstance().getActions(true).size() > 0);
  }
}
