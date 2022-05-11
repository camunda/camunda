/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumSet;
import java.util.stream.Stream;

/** Collection of utilities for unit and integration tests. */
final class TestSupport {
  private TestSupport() {}

  /**
   * Returns a stream of value types which are export-able by the exporter, i.e. the ones with an
   * index template.
   *
   * <p>Issue https://github.com/camunda/zeebe/issues/8337 should fix this and ensure all types have
   * an index template.
   */
  static Stream<ValueType> provideValueTypes() {
    final var excludedValueTypes =
        EnumSet.of(
            ValueType.SBE_UNKNOWN,
            ValueType.NULL_VAL,
            ValueType.TIMER,
            ValueType.PROCESS_INSTANCE_RESULT,
            ValueType.DEPLOYMENT_DISTRIBUTION,
            ValueType.PROCESS_EVENT,
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
    return EnumSet.complementOf(excludedValueTypes).stream();
  }
}
