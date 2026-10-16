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

class ScimGroupFilterTest {

  @Test
  void shouldReturnEmptyWhenNoFilterIsGiven() throws Exception {
    // when / then
    assertThat(ScimGroupFilter.parseDisplayNameEquals(null)).isEmpty();
    assertThat(ScimGroupFilter.parseDisplayNameEquals(" ")).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "displayName eq \"Engineering\"",
        "DISPLAYNAME EQ \"Engineering\"",
        "DisplayName Eq \"Engineering\""
      })
  void shouldParseDisplayNameEqualityCaseInsensitively(final String filter) throws Exception {
    // when
    final var displayName = ScimGroupFilter.parseDisplayNameEquals(filter);

    // then
    assertThat(displayName).contains("Engineering");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "displayName ne \"x\"",
        "displayName co \"x\"",
        "externalId eq \"x\"",
        "displayName eq 5",
        "displayName pr",
        "displayName eq \"a\" and externalId eq \"b\"",
        "members[value eq \"alice\"]"
      })
  void shouldRejectUnsupportedFilters(final String filter) {
    // when / then
    assertThatThrownBy(() -> ScimGroupFilter.parseDisplayNameEquals(filter))
        .isInstanceOfSatisfying(
            BadRequestException.class,
            e -> assertThat(e.getScimError().getScimType()).isEqualTo("invalidFilter"));
  }
}
