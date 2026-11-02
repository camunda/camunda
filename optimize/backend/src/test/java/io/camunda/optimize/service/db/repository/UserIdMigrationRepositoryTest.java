/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static io.camunda.optimize.service.db.repository.UserIdMigrationRepository.maskForLog;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserIdMigrationRepositoryTest {

  @ParameterizedTest
  @CsvSource({
    "user@example.com, u***@example.com",
    "samlp|Okta|user@example.com, samlp|Okta|u***@example.com",
    "@example.com, ***@example.com",
    "auth0|abc123, auth0|abc123"
  })
  void shouldMaskTheLocalPartOfAnEmail(final String userId, final String expected) {
    // when
    final String masked = maskForLog(userId);

    // then
    assertThat(masked).isEqualTo(expected);
  }
}
