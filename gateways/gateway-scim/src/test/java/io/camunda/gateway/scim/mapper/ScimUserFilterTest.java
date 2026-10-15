/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.unboundid.scim2.common.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScimUserFilterTest {

  @Test
  void shouldReturnEmptyWhenNoFilterIsGiven() throws Exception {
    // when / then
    assertThat(ScimUserFilter.parseUserNameEquals(null)).isEmpty();
    assertThat(ScimUserFilter.parseUserNameEquals(" ")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "userName eq \"alice@example.com\"",
        "USERNAME EQ \"alice@example.com\"",
        "UserName Eq \"alice@example.com\""
      })
  void shouldParseUserNameEqualityCaseInsensitively(final String filter) throws Exception {
    // when
    final var userName = ScimUserFilter.parseUserNameEquals(filter);

    // then
    assertThat(userName).contains("alice@example.com");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "userName ne \"alice\"",
        "userName co \"ali\"",
        "externalId eq \"alice\"",
        "active eq true",
        "userName eq 5",
        "userName pr",
        "userName eq \"a\" and active eq true",
        "emails[type eq \"work\"]",
        "name.givenName eq \"a\""
      })
  void shouldRejectUnsupportedFilters(final String filter) {
    // when / then
    assertThatThrownBy(() -> ScimUserFilter.parseUserNameEquals(filter))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidFilter"));
  }

  @Test
  void shouldRejectUnparsableFilter() {
    // when / then
    assertThatThrownBy(() -> ScimUserFilter.parseUserNameEquals("userName eq"))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidFilter"));
  }
}
