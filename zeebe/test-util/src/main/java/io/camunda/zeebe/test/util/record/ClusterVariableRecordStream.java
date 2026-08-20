/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import java.util.stream.Stream;

public final class ClusterVariableRecordStream
    extends ExporterRecordStream<ClusterVariableRecordValue, ClusterVariableRecordStream> {

  public ClusterVariableRecordStream(
      final Stream<Record<ClusterVariableRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ClusterVariableRecordStream supply(
      final Stream<Record<ClusterVariableRecordValue>> wrappedStream) {
    return new ClusterVariableRecordStream(wrappedStream);
  }

  public ClusterVariableRecordStream withName(final String name) {
    return valueFilter(v -> v.getName().equals(name));
  }

  public ClusterVariableRecordStream withValue(final String value) {
    return valueFilter(v -> v.getValue().equals(value));
  }
}
