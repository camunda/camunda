/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RdbmsPrefixNormalizationTest {

  @ParameterizedTest
  @CsvSource({"ta_, TA_", "Ta_, TA_", "TA_, TA_", "c8, C8", "my_prefix_, MY_PREFIX_"})
  void shouldUpperCasePrefix(final String configured, final String expected) {
    // given
    final Rdbms rdbms = new Rdbms();

    // when
    rdbms.setPrefix(configured);

    // then
    assertThat(rdbms.getPrefix()).isEqualTo(expected);
  }

  @Test
  void shouldKeepNullPrefix() {
    // given
    final Rdbms rdbms = new Rdbms();

    // when
    rdbms.setPrefix(null);

    // then
    assertThat(rdbms.getPrefix()).isNull();
  }

  /**
   * {@code ti_} upper-cases differently under {@code tr-TR}, where the dotless {@code i} becomes
   * {@code İ} and yields a prefix that no longer matches the created tables. Asserting against both
   * results pins the normalization to {@link Locale#ROOT} without mutating the JVM-wide default
   * locale, which would leak into unrelated tests.
   */
  @Test
  void shouldUpperCaseIndependentlyOfDefaultLocale() {
    // given
    final Rdbms rdbms = new Rdbms();

    // when
    rdbms.setPrefix("ti_");

    // then
    assertThat(rdbms.getPrefix())
        .isEqualTo("ti_".toUpperCase(Locale.ROOT))
        .isNotEqualTo("ti_".toUpperCase(Locale.forLanguageTag("tr-TR")));
  }
}
