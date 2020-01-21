// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

/**
 * @author peter
 */
public abstract class LazyInstance<T> extends NotNullLazyValue<T>{
  protected abstract Class<T> getInstanceClass() throws ClassNotFoundException;

  @Override
  @NotNull
  protected final T compute() {
    try {
      Class<T> tClass = getInstanceClass();
      Constructor<T> constructor = tClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return tClass.newInstance();
    }
    catch (InstantiationException | NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
