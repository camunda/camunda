/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OidcUserInfoAugmentationConfigurationTest {

  @Test
  void defaultsAreDisabledWithFiveMinuteTtlAndTenThousandEntries() {
    final var config = new OidcUserInfoAugmentationConfiguration();

    assertThat(config.isEnabled()).isFalse();
    assertThat(config.getCacheTtl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(config.getCacheMaxSize()).isEqualTo(10_000);
  }

  @Test
  void oidcAuthenticationConfigurationExposesNonNullAugmentationByDefault() {
    final var oidc = new OidcAuthenticationConfiguration();

    assertThat(oidc.getUserInfoAugmentation()).isNotNull();
    assertThat(oidc.getUserInfoAugmentation().isEnabled()).isFalse();
  }
}
