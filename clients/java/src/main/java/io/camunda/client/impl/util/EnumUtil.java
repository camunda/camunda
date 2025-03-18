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
package io.camunda.client.impl.util;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnumUtil {

  private static final String UNKNOWN_ENUM_VALUE = "UNKNOWN_ENUM_VALUE";
  private static final String UNKNOWN_DEFAULT_OPEN_API = "UNKNOWN_DEFAULT_OPEN_API";

  private static final Logger LOGGER = LoggerFactory.getLogger(EnumUtil.class);

  private EnumUtil() {}

  public static <E> void logUnknownEnumValue(
      final Object value, final String enumName, final E[] validValues) {
    LOGGER.debug(
        "Unexpected {} '{}', should be one of {}", enumName, value, Arrays.toString(validValues));
  }

  public static <E1 extends Enum<E1>, E2 extends Enum<E2>> E2 convert(
      E1 source, Class<E2> targetClass) {
    if (source == null) {
      return null;
    }
    try {
      if (source.name().equals(UNKNOWN_ENUM_VALUE)) {
        return E2.valueOf(targetClass, UNKNOWN_DEFAULT_OPEN_API);
      }
      if (source.name().equals(UNKNOWN_DEFAULT_OPEN_API)) {
        return E2.valueOf(targetClass, UNKNOWN_ENUM_VALUE);
      }
      return E2.valueOf(targetClass, source.name());
    } catch (final IllegalArgumentException e) {
      logUnknownEnumValue(source, source.getClass().getName(), targetClass.getEnumConstants());
      throw new RuntimeException(e);
    }
  }
}
