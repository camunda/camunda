/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class ConfigValidationTest {

  @Test
  public void shouldReturnValueWhenAllMatch() {
    final var es = ConfigValidation.allMatch(null, null, Map.of("app-1", "ES", "app-2", "ES"));
    assertThat(es).isEqualTo("ES");
  }

  @Test
  public void shouldThrowExceptionWhenNoMatch() {
    assertThatThrownBy(
            () ->
                ConfigValidation.allMatch(
                    null, "Different: %s", Map.of("app-1", "ES", "app-2", "OS")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Different:")
        // Split the message as the Map.toString is not stable
        .hasMessageContaining("app-2=OS")
        .hasMessageContaining("app-1=ES");
  }

  @Test
  public void shouldThrowExceptionWhenEmpty() {
    assertThatThrownBy(() -> ConfigValidation.allMatch("empty", null, Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty");
  }

  @Test
  public void shouldSkipBasedOnPredicate() {
    final var es =
        ConfigValidation.allMatch(
            null,
            null,
            Map.of("app-1", "ES", "app-2", "ES", "app-3", ""),
            ConfigValidation.skipEmpty);
    assertThat(es).isEqualTo("ES");
  }

  @Test
  public void shouldThrowWhenAllAreSkipped() {

    assertThatThrownBy(
            () ->
                ConfigValidation.allMatch(
                    "empty",
                    null,
                    Map.of("app-1", "", "app-2", "", "app-3", ""),
                    ConfigValidation.skipEmpty))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("empty");
  }
}
