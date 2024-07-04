/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.util;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Utility class for common tasks with {@link AtomicReference} instances. */
public final class AtomicUtil {

  private AtomicUtil() {}

  /**
   * Locklessly replaces the value of an atomic reference using the provided replacer function.
   *
   * <p>If the value of the atomic reference has been replaced by another thread in the meantime,
   * the replacer function is called again.
   *
   * @param ref The atomic reference that holds the value to replace
   * @param replacer The replacer function used to provide a new value to the atomic reference
   * @return The previous value of the atomic reference, or null if the value was not replaced
   * @param <T> The type of the value of the atomic reference
   */
  public static <T> T replace(final AtomicReference<T> ref, final Function<T, T> replacer) {
    T currentVal;
    T newVal;
    do {
      currentVal = ref.get();
      newVal = replacer.apply(currentVal);
    } while (!ref.compareAndSet(currentVal, newVal));
    return currentVal == newVal ? null : currentVal;
  }
}
