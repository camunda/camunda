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
package io.camunda.client.impl.worker;

final class SneakyThrow {

  private SneakyThrow() {}

  /**
   * Throws {@code failure} without declaring it, the way Kotlin code or Lombok's
   * {@code @SneakyThrows} can throw a checked exception through a Java interface.
   *
   * <p>{@code () -> sneakyThrow(new IOException("backend unreachable"))}
   */
  static void sneakyThrow(final Throwable failure) {
    SneakyThrow.<RuntimeException>throwUnchecked(failure);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwUnchecked(final Throwable failure) throws E {
    throw (E) failure;
  }
}
