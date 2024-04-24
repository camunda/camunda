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
package io.camunda.zeebe.spring.client.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.zeebe.spring.client.configuration.PropertyUtil;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class PropertyUtilTest {
  @Test
  void shouldPreferLegacy() {
    final String property =
        PropertyUtil.getOrLegacyOrDefault(
            "Test", () -> "prop", () -> "legacy", "default", new HashMap<>());
    assertThat(property).isEqualTo("legacy");
  }

  @Test
  void shouldApplyDefault() {
    final String property =
        PropertyUtil.getOrLegacyOrDefault(
            "Test", () -> null, () -> null, "default", new HashMap<>());
    assertThat(property).isEqualTo("default");
  }

  @Test
  void shouldIgnoreDefaultOnLegacy() {
    final String property =
        PropertyUtil.getOrLegacyOrDefault(
            "Test", () -> "prop", () -> "default", "default", new HashMap<>());
    assertThat(property).isEqualTo("prop");
  }

  @Test
  void shouldHandleExceptionOnPropertySupplier() {
    final String property =
        PropertyUtil.getOrLegacyOrDefault(
            "Test",
            () -> {
              throw new NullPointerException();
            },
            () -> null,
            "default",
            new HashMap<>());
    assertThat(property).isEqualTo("default");
  }

  @Test
  void shouldHandleExceptionOnLegacyPropertySupplier() {
    final String property =
        PropertyUtil.getOrLegacyOrDefault(
            "Test",
            () -> null,
            () -> {
              throw new NullPointerException();
            },
            "default",
            new HashMap<>());
    assertThat(property).isEqualTo("default");
  }
}
