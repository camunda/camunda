/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.util.stream.Stream;

public final class ProcessRecordStream extends ExporterRecordStream<Process, ProcessRecordStream> {

  public ProcessRecordStream(final Stream<Record<Process>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ProcessRecordStream supply(final Stream<Record<Process>> wrappedStream) {
    return new ProcessRecordStream(wrappedStream);
  }

  public ProcessRecordStream withResourceName(final String resourceName) {
    return valueFilter(v -> v.getResourceName().equals(resourceName));
  }

  public ProcessRecordStream withBpmnProcessId(final String bpmnProcessId) {
    return valueFilter(v -> v.getBpmnProcessId().equals(bpmnProcessId));
  }

  public ProcessRecordStream withProcessDefinitionKey(final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public ProcessRecordStream withVersion(final int version) {
    return valueFilter(v -> v.getVersion() == version);
  }
}
