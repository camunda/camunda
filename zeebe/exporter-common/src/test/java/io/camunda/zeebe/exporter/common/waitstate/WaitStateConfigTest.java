/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateElementType;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class WaitStateConfigTest {

  @ParameterizedTest
  @MethodSource("unambiguousMappings")
  void shouldMapElementTypeToWaitStateType(
      final WaitStateElementType elementType, final WaitStateType expected) {
    // when / then
    assertThat(WaitStateConfig.getWaitStateType(elementType)).contains(expected);
  }

  @ParameterizedTest
  @EnumSource(
      value = WaitStateElementType.class,
      names = {"INTERMEDIATE_CATCH_EVENT", "BOUNDARY_EVENT", "EVENT_BASED_GATEWAY"})
  void shouldReturnEmptyForEventDrivenElements(final WaitStateElementType elementType) {
    // when / then
    assertThat(WaitStateConfig.getWaitStateType(elementType)).isEmpty();
  }

  private static Stream<Arguments> unambiguousMappings() {
    return Stream.of(
        Arguments.of(WaitStateElementType.SERVICE_TASK, WaitStateType.JOB),
        Arguments.of(WaitStateElementType.SEND_TASK, WaitStateType.JOB),
        Arguments.of(WaitStateElementType.BUSINESS_RULE_TASK, WaitStateType.JOB),
        Arguments.of(WaitStateElementType.SCRIPT_TASK, WaitStateType.JOB),
        Arguments.of(WaitStateElementType.USER_TASK, WaitStateType.USER_TASK),
        Arguments.of(WaitStateElementType.RECEIVE_TASK, WaitStateType.MESSAGE),
        Arguments.of(WaitStateElementType.CALL_ACTIVITY, WaitStateType.CALL_ACTIVITY));
  }
}
