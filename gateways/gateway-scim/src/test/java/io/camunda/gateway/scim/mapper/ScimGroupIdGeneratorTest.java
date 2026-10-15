/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.scim.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.validation.IdentifierValidator;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ScimGroupIdGeneratorTest {

  private static final Pattern DEFAULT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");

  private final ScimGroupIdGenerator generator =
      new ScimGroupIdGenerator(new IdentifierValidator(DEFAULT_ID_PATTERN, DEFAULT_ID_PATTERN));

  @Test
  void shouldLowercaseAndReplaceRunsOfIllegalCharacters() {
    // when
    final var id = generator.generate("  Engineering & Ops / EMEA!  ", null);

    // then
    assertThat(id).isEqualTo("engineering-ops-emea");
  }

  @Test
  void shouldKeepCharactersTheIdentifierPatternAllows() {
    // when
    final var id = generator.generate("Team_A.v2+new@corp", null);

    // then
    assertThat(id).isEqualTo("team_a.v2+new@corp");
  }

  @Test
  void shouldFallBackToExternalIdWhenDisplayNameSanitizesToNothing() {
    // when
    final var id = generator.generate("!!! ???", "Ext ID 42");

    // then
    assertThat(id).isEqualTo("ext-id-42");
  }

  @Test
  void shouldFallBackToRandomUuidWhenNothingUsableIsLeft() {
    // when
    final var id = generator.generate("***", "///");

    // then
    assertThat(UUID.fromString(id)).isNotNull();
  }

  @Test
  void shouldTreatNonLatinLettersAsIllegalUnderTheDefaultPattern() {
    // when
    final var id = generator.generate("Entwicklung Büro", null);

    // then
    assertThat(id).isEqualTo("entwicklung-b-ro");
  }

  @Test
  void shouldHonourAConfiguredIdentifierPattern() {
    // given
    final var letters = Pattern.compile("^[a-z0-9-]+$");
    final var strict = new ScimGroupIdGenerator(new IdentifierValidator(letters, letters));

    // when
    final var id = strict.generate("Team_42 Alpha", null);

    // then
    assertThat(id).isEqualTo("team-42-alpha");
  }
}
