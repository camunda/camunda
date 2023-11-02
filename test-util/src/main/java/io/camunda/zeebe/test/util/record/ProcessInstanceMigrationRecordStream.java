/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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

  public ProcessInstanceMigrationRecordStream withTargetProcessDefinitionKey(
      final long processDefinitionKey) {
    return valueFilter(v -> v.getTargetProcessDefinitionKey() == processDefinitionKey);
  }
}
