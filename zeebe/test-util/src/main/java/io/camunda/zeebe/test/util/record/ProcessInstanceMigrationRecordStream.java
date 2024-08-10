/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import java.util.stream.Stream;

public final class ProcessInstanceMigrationRecordStream
    extends ExporterRecordStream<
        ProcessInstanceMigrationRecordValue, ProcessInstanceMigrationRecordStream> {

  public ProcessInstanceMigrationRecordStream(
      final Stream<Record<ProcessInstanceMigrationRecordValue>> records) {
    super(records);
  }

  @Override
  protected ProcessInstanceMigrationRecordStream supply(
      final Stream<Record<ProcessInstanceMigrationRecordValue>> wrappedStream) {
    return new ProcessInstanceMigrationRecordStream(wrappedStream);
  }

  public ProcessInstanceMigrationRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }
}
