// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PositionTrackerTestAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    for (Balloon.Position position : Balloon.Position.values()) {
      JLabel popupContent = new JLabel("PositionTracker ["+position+"]", SwingConstants.CENTER);
      popupContent.setPreferredSize(new JBDimension(200, 50));
      JBPopupFactory popupFactory = JBPopupFactory.getInstance();
      popupFactory
        .createDialogBalloonBuilder(popupContent, null)
        .createBalloon()
        .show(new PositionTracker<Balloon>(editor.getContentComponent()) {
          @Override
          public RelativePoint recalculateLocation(Balloon balloon) {
            return popupFactory.guessBestPopupLocation(editor);
          }
        }, position);

    }
  }
}
