/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import java.util.stream.Stream;

public class CaseRecordStream extends ExporterRecordStream<CaseRecordValue, CaseRecordStream> {

  public CaseRecordStream(final Stream<Record<CaseRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected CaseRecordStream supply(final Stream<Record<CaseRecordValue>> wrappedStream) {
    return new CaseRecordStream(wrappedStream);
  }

  public CaseRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }
}
