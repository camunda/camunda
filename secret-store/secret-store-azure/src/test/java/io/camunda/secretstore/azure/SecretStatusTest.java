/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.security.keyvault.secrets.models.SecretProperties;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SecretStatusTest {

  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

  @Test
  void shouldReportADisabledSecretAsDisabledEvenWhenItIsAlsoExpired() {
    // given
    final var properties =
        new SecretProperties()
            .setEnabled(false)
            .setExpiresOn(NOW.minusSeconds(60).atOffset(ZoneOffset.UTC));

    // when
    final var status = SecretStatus.of(properties, NOW);

    // then
    assertThat(status).isEqualTo(SecretStatus.DISABLED);
  }

  @Test
  void shouldReportASecretPastBothDatesAsExpiredRatherThanNotYetActive() {
    // given
    final var properties =
        new SecretProperties()
            .setEnabled(true)
            .setExpiresOn(NOW.minusSeconds(60).atOffset(ZoneOffset.UTC))
            .setNotBefore(NOW.plusSeconds(60).atOffset(ZoneOffset.UTC));

    // when
    final var status = SecretStatus.of(properties, NOW);

    // then
    assertThat(status).isEqualTo(SecretStatus.EXPIRED);
  }

  @Test
  void shouldReportASecretWithoutDatesAsActive() {
    // given
    final var properties = new SecretProperties();

    // when
    final var status = SecretStatus.of(properties, NOW);

    // then
    assertThat(status).isEqualTo(SecretStatus.ACTIVE);
  }
}
