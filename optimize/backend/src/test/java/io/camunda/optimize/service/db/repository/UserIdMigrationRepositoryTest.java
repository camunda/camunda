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

import org.junit.jupiter.api.Test;
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

  @Test
  void shouldMaskTheUserIdsInTheWholeCauseChainAndKeepTheStackTraces() {
    // given
    final IllegalStateException root =
        new IllegalStateException("shard failed for term [samlp|Okta|user@example.com]");
    final RuntimeException failure = new RuntimeException("task failed for user@example.com", root);

    // when
    final Throwable sanitized =
        UserIdMigrationRepository.sanitize(
            failure, "samlp|Okta|user@example.com", "user@example.com");

    // then
    assertThat(sanitized)
        .hasMessage("java.lang.RuntimeException: task failed for u***@example.com");
    assertThat(sanitized.getStackTrace()).isEqualTo(failure.getStackTrace());
    assertThat(sanitized.getCause())
        .hasMessage(
            "java.lang.IllegalStateException: shard failed for term [samlp|Okta|u***@example.com]")
        .hasNoCause();
    assertThat(sanitized.getCause().getStackTrace()).isEqualTo(root.getStackTrace());
  }
}
