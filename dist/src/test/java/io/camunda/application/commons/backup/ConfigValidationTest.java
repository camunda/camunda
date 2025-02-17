/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static io.camunda.application.commons.backup.ConfigValidation.allMatch;
import static io.camunda.application.commons.backup.ConfigValidation.skipEmpty;
import static io.camunda.application.commons.backup.ConfigValidation.skipEmptyOptional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ConfigValidationTest {

  @Test
  public void shouldReturnValueWhenAllMatch() {
    final var es = allMatch(() -> null, null, Map.of("app-1", "ES", "app-2", "ES"));
    assertThat(es).isEqualTo("ES");
  }

  @Test
  public void shouldThrowExceptionWhenNoMatch() {
    assertThatThrownBy(
            () ->
                allMatch(
                    () -> null,
                    m -> String.format("Different: %s", m),
                    Map.of("app-1", "ES", "app-2", "OS")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Different:")
        // Split the message as the Map.toString is not stable
        .hasMessageContaining("app-2=OS")
        .hasMessageContaining("app-1=ES");
  }

  @Test
  public void shouldThrowExceptionWhenEmpty() {
    assertThat(allMatch(() -> null, null, new HashMap<String, String>())).isEqualTo(null);
  }

  @Test
  public void shouldSkipBasedOnPredicate() {
    final var es =
        allMatch(() -> null, null, Map.of("app-1", "ES", "app-2", "ES", "app-3", ""), skipEmpty);
    assertThat(es).isEqualTo("ES");
  }

  @Test
  public void shouldSkipEmptyOptionalIfToldTo() {
    final Object nullObj =
        allMatch(() -> null, null, Map.of("app-1", Optional.empty(), "app-2", Optional.empty()));
    assertThat(nullObj).isEqualTo(Optional.empty());

    assertThat(
            allMatch(
                () -> null,
                null,
                Map.of("app-1", Optional.empty(), "app-2", Optional.empty()),
                skipEmptyOptional()))
        .isEqualTo(null);
  }

  @Test
  public void shouldThrowWhenAllAreSkipped() {
    assertThat(allMatch(() -> null, null, Map.of("app-1", "", "app-2", "", "app-3", ""), skipEmpty))
        .isEqualTo(null);
  }
}
