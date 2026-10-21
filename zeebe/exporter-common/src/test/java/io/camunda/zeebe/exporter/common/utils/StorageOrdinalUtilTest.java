/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class StorageOrdinalUtilTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 1000, 1001, Integer.MAX_VALUE})
  void shouldBeOrdinalControlledForAssignedOrdinal(final int ordinal) {
    // given
    final StorageOrdinalRelated record = () -> ordinal;

    // when / then
    assertThat(StorageOrdinalUtil.isOrdinalControlled(record)).isTrue();
    assertThat(StorageOrdinalUtil.ordinalIfOrdinalControlled(record)).contains(ordinal);
  }

  @Test
  void shouldNotBeOrdinalControlledForMainIndex() {
    // given
    final StorageOrdinalRelated record = () -> StorageOrdinalRelated.MAIN_INDEX;

    // when / then
    assertThat(StorageOrdinalUtil.isOrdinalControlled(record)).isFalse();
    assertThat(StorageOrdinalUtil.ordinalIfOrdinalControlled(record)).isEmpty();
  }

  @Test
  void shouldNotBeOrdinalControlledForNotOrdinalControlledRecord() {
    // given
    final StorageOrdinalRelated record = () -> StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED;

    // when / then
    assertThat(StorageOrdinalUtil.isOrdinalControlled(record)).isFalse();
    assertThat(StorageOrdinalUtil.ordinalIfOrdinalControlled(record)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {-2, -100, Integer.MIN_VALUE})
  void shouldRejectOrdinalBelowNotOrdinalControlled(final int ordinal) {
    // given
    final StorageOrdinalRelated record = () -> ordinal;

    // when / then
    assertThatThrownBy(() -> StorageOrdinalUtil.isOrdinalControlled(record))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid storage ordinal: " + ordinal);

    assertThatThrownBy(() -> StorageOrdinalUtil.ordinalIfOrdinalControlled(record))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid storage ordinal: " + ordinal);
  }
}
