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
