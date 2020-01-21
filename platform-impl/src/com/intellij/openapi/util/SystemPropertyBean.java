// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author gregsh
 */
public final class SystemPropertyBean implements PluginAware {
  private static final ExtensionPointName<SystemPropertyBean> EP_NAME = ExtensionPointName.create("com.intellij.systemProperty");

  private PluginDescriptor myPluginDescriptor;

  public static void initSystemProperties() {
    EP_NAME.forEachExtensionSafe(bean -> {
      if (System.getProperty(bean.name) == null) {
        System.setProperty(bean.name, bean.value);
      }
    });
  }

  @Attribute("name")
  public String name;

  @Attribute("value")
  public String value;

  @Override
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  @Transient
  @Nullable
  public PluginDescriptor getPluginDescriptor() {
    return myPluginDescriptor;
  }
}
