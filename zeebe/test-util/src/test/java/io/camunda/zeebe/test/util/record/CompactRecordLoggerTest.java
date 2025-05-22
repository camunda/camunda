/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompactRecordLoggerTest {
  private CompactRecordLogger compactRecordLogger;

  @BeforeEach
  public void beforeEach() {
    compactRecordLogger = new CompactRecordLogger(List.of());
  }

  @ParameterizedTest
  @EnumSource(ValueType.class)
  public void testAllValueTypesAreSupported(final ValueType valueType) {
    assertThat(compactRecordLogger.getSupportedValueTypes()).contains(valueType);
  }
}
