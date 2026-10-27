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
package io.camunda.client.jobhandling.parameter;

import java.util.Arrays;

public enum KeyTargetType {
  LONG(new Class<?>[] {Long.class, long.class}),
  STRING(new Class<?>[] {String.class});

  private final Class<?>[] parameterTypes;

  KeyTargetType(final Class<?>[] parameterTypes) {
    this.parameterTypes = parameterTypes;
  }

  public static boolean isValidParameterType(final Class<?> parameterType) {
    for (final KeyTargetType keyTargetType : KeyTargetType.values()) {
      for (final Class<?> type : keyTargetType.parameterTypes) {
        if (type.isAssignableFrom(parameterType)) {
          return true;
        }
      }
    }
    return false;
  }

  public static KeyTargetType from(final Class<?> parameterType) {
    for (final KeyTargetType keyTargetType : KeyTargetType.values()) {
      for (final Class<?> type : keyTargetType.parameterTypes) {
        if (type.isAssignableFrom(parameterType)) {
          return keyTargetType;
        }
      }
    }
    throw new IllegalArgumentException(
        "Unsupported target type: "
            + parameterType
            + ", supported types are: "
            + Arrays.toString(values()));
  }
}
