// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.remote.ext.CredentialsLanguageContribution;
import com.intellij.remote.ext.CredentialsManager;
import com.intellij.remote.ext.CredentialsTypeEx;
import com.intellij.remote.ui.CredentialsEditorProvider;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FilteringIterator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CredentialsTypeUtil {
  private CredentialsTypeUtil() {
  }

  public static boolean isCredentialsTypeSupportedForLanguage(@NotNull CredentialsType<?> credentialsType,
                                                              @NotNull Class<?> languageContributionMarkerClass) {
    // TODO add language contributors for Python and Node JS
    for (CredentialsTypeEx<?> typeEx : CredentialsManager.getInstance().getExTypes()) {
      if (credentialsType.equals(typeEx)) {
        CredentialsEditorProvider editorProvider = ObjectUtils.tryCast(typeEx, CredentialsEditorProvider.class);
        if (editorProvider != null) {
          List<CredentialsLanguageContribution> contributions = getContributions(languageContributionMarkerClass);
          if (!contributions.isEmpty()) {
            for (CredentialsLanguageContribution contribution : contributions) {
              if (contribution.getType() == typeEx && editorProvider.isAvailable(contribution)) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }

  @NotNull
  public static <T> List<CredentialsLanguageContribution> getContributions(@NotNull Class<T> languageContributionMarkerInterface) {
    return ContainerUtil.filter(CredentialsLanguageContribution.EP_NAME.getExtensions(),
                                FilteringIterator.instanceOf(languageContributionMarkerInterface));
  }
}
