/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link CamundaContainer#resolve(String, String, String)} honors the plain
 * unprefixed env var value, so a CI-provided image override takes effect instead of silently
 * falling back to the Docker Hub nightly image.
 *
 * <p>The property argument uses a name with no matching {@code TESTCONTAINERS_}-prefixed env var or
 * {@code .testcontainers.properties} entry, so Testcontainers' lookup misses and the resolver's
 * unprefixed-env fallback is exercised.
 */
class CamundaContainerImageResolutionTest {

  private static final String UNSET_PROPERTY = "camunda.container.test.unset.image";

  @Test
  void shouldUseFallbackWhenEnvValueMissing() {
    // when -- no Testcontainers source and no plain env value
    final String resolved = CamundaContainer.resolve(UNSET_PROPERTY, null, "camunda/camunda");

    // then -- the hardcoded fallback is used
    assertThat(resolved).isEqualTo("camunda/camunda");
  }

  @Test
  void shouldUseFallbackWhenEnvValueBlank() {
    // when -- the plain env value is present but blank
    final String resolved = CamundaContainer.resolve(UNSET_PROPERTY, "  ", "camunda/camunda");

    // then -- the hardcoded fallback is used
    assertThat(resolved).isEqualTo("camunda/camunda");
  }

  @Test
  void shouldHonorUnprefixedEnvValueOverFallback() {
    // when -- a plain (unprefixed) env value is provided and no Testcontainers source is set
    final String resolved =
        CamundaContainer.resolve(
            UNSET_PROPERTY, "localhost:5000/camunda/camunda", "camunda/camunda");

    // then -- the plain env value wins over the hardcoded fallback
    assertThat(resolved).isEqualTo("localhost:5000/camunda/camunda");
  }
}
