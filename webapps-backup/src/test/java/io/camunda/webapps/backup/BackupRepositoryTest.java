/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class BackupRepositoryTest {

  @Nested
  class BackupIdPattern {
    @ParameterizedTest
    @ValueSource(strings = {""})
    public void shouldMatchDefaultPattern(final String pattern) {
      assertThat(BackupRepository.validPattern(pattern)).isEqualTo("*");
    }

    @ParameterizedTest
    @ValueSource(strings = {"23", "*", "12390*", "1230891283*", "20250401*", "20250401"})
    public void shouldBeAValidPattern(final String pattern) {
      assertThat(BackupRepository.validPattern(pattern)).isEqualTo(pattern);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "23a",
          "backup24*",
          "**",
          "12389128391829381923891238",
          "  ",
          "\n",
          "\t",
          "abcd123*",
          "123*?query=all"
        })
    public void shouldNotBeAvalidPattern(final String pattern) {
      assertThatThrownBy(() -> BackupRepository.validPattern(pattern))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
