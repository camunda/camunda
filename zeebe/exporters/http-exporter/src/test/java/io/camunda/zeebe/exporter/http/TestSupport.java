/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http;

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
   * <p>Issue https://github.com/camunda/camunda/issues/8337 should fix this and ensure all types
   * have an index template.
   */
  static Stream<ValueType> provideValueTypes() {
    final var excludedValueTypes =
        EnumSet.of(
            ValueType.SBE_UNKNOWN,
            ValueType.NULL_VAL,
            ValueType.PROCESS_INSTANCE_RESULT,
            ValueType.CLOCK,
            ValueType.SCALE,
            // these are not yet supported
            ValueType.AUTHORIZATION,
            ValueType.USER,
            ValueType.ROLE,
            ValueType.TENANT,
            ValueType.GROUP,
            ValueType.MAPPING_RULE,
            ValueType.IDENTITY_SETUP,
            ValueType.RESOURCE,
            ValueType.BATCH_OPERATION_CREATION,
            ValueType.BATCH_OPERATION_CHUNK,
            ValueType.BATCH_OPERATION_EXECUTION,
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
            ValueType.BATCH_OPERATION_INITIALIZATION,
            ValueType.USAGE_METRIC,
            ValueType.MULTI_INSTANCE);
    return EnumSet.complementOf(excludedValueTypes).stream();
  }
}
