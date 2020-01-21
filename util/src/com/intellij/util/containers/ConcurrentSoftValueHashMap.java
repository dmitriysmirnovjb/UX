/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.util.containers;

import org.jetbrains.annotations.NotNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * Concurrent strong key:K -> soft value:V map
 * Null keys are NOT allowed
 * Null values are NOT allowed
 * Use {@link ContainerUtil#createConcurrentSoftValueMap()} to create this
 */
final class ConcurrentSoftValueHashMap<K,V> extends ConcurrentRefValueHashMap<K,V> {
  private static class MySoftReference<K, V> extends SoftReference<V> implements ValueReference<K, V> {
    private final K key;
    private MySoftReference(@NotNull K key, @NotNull V referent, @NotNull ReferenceQueue<V> q) {
      super(referent, q);
      this.key = key;
    }

    @NotNull
    @Override
    public K getKey() {
      return key;
    }

    // When referent is collected, equality should be identity-based (for the processQueues() remove this very same SoftValue)
    // otherwise it's just canonical equals on referents for replace(K,V,V) to work
    @Override
    public final boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      //noinspection unchecked
      ValueReference<K,V> that = (ValueReference<K, V>)o;

      V v = get();
      V thatV = that.get();
      return key.equals(that.getKey()) && v != null && v.equals(thatV);
    }
  }

  @NotNull
  @Override
  ValueReference<K, V> createValueReference(@NotNull K key, @NotNull V value) {
    return new MySoftReference<>(key, value, myQueue);
  }
}