/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

public class FilterUtilTest {

  @Test
  void shouldTreatNullStringAsEmpty() {
    assertThat(FilterUtil.hasAnyNonEmpty((String) null)).isFalse();
  }

  @Test
  void shouldTreatEmptyStringAsNonEmpty() {
    // an explicitly supplied "" is a meaningful exact-match criterion, not an absent one
    assertThat(FilterUtil.hasAnyNonEmpty("")).isTrue();
  }

  @Test
  void shouldTreatNullOrEmptyCollectionAsEmpty() {
    assertThat(FilterUtil.hasAnyNonEmpty((List<?>) null)).isFalse();
    assertThat(FilterUtil.hasAnyNonEmpty(List.of())).isFalse();
  }

  @Test
  void shouldTreatNonEmptyCollectionAsNonEmpty() {
    assertThat(FilterUtil.hasAnyNonEmpty(List.of("value"))).isTrue();
  }
}
