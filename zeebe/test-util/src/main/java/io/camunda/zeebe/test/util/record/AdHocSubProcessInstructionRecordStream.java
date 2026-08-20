/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import java.util.stream.Stream;

public class AdHocSubProcessInstructionRecordStream
    extends ExporterRecordStream<
        AdHocSubProcessInstructionRecordValue, AdHocSubProcessInstructionRecordStream> {

  public AdHocSubProcessInstructionRecordStream(
      final Stream<Record<AdHocSubProcessInstructionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected AdHocSubProcessInstructionRecordStream supply(
      final Stream<Record<AdHocSubProcessInstructionRecordValue>> wrappedStream) {
    return new AdHocSubProcessInstructionRecordStream(wrappedStream);
  }

  public AdHocSubProcessInstructionRecordStream withAdHocSubProcessInstanceKey(
      final long adHocSubProcessInstanceKey) {
    return valueFilter(
        record -> record.getAdHocSubProcessInstanceKey() == adHocSubProcessInstanceKey);
  }

  public AdHocSubProcessInstructionRecordStream limitToAdHocSubProcessInstanceCompleted() {
    return limit(
        r ->
            r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                && r.getKey() == r.getValue().getAdHocSubProcessInstanceKey());
  }
}
