// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.schemes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DescriptionAwareSchemeActions<T extends Scheme> extends AbstractSchemeActions<T> {
  protected DescriptionAwareSchemeActions(@NotNull AbstractDescriptionAwareSchemesPanel<T> schemesPanel) {
    super(schemesPanel);
  }

  @Nullable
  public abstract String getDescription(@NotNull T scheme);

  protected abstract void setDescription(@NotNull T scheme, @NotNull String newDescription);

  @Override
  protected void addAdditionalActions(@NotNull List<? super AnAction> defaultActions) {
    defaultActions.add(new DumbAwareAction() {

      @Override
      public void update(@NotNull AnActionEvent e) {
        T scheme = getSchemesPanel().getSelectedScheme();
        if (scheme == null) {
          e.getPresentation().setEnabledAndVisible(false);
          return;
        }
        final String text = getDescription(scheme) == null ? "Add Description..." : "Edit Description...";
        e.getPresentation().setEnabledAndVisible(true);
        e.getPresentation().setText(text);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ((AbstractDescriptionAwareSchemesPanel<T>) mySchemesPanel).editDescription(getDescription(getSchemesPanel().getSelectedScheme()));
      }
    });
  }

  @Override
  protected void onSchemeChanged(@Nullable T scheme) {
    if (scheme != null) {
      ((AbstractDescriptionAwareSchemesPanel<T>) mySchemesPanel).showDescription(scheme);
    }
  }
}
