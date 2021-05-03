/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.record;

import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.deployment.Process;
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
}
