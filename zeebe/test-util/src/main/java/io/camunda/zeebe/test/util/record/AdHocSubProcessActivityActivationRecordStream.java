/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessActivityActivationRecordValue;
import java.util.stream.Stream;

public class AdHocSubProcessActivityActivationRecordStream
    extends ExporterRecordStream<
        AdHocSubProcessActivityActivationRecordValue,
        AdHocSubProcessActivityActivationRecordStream> {

  public AdHocSubProcessActivityActivationRecordStream(
      final Stream<Record<AdHocSubProcessActivityActivationRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected AdHocSubProcessActivityActivationRecordStream supply(
      final Stream<Record<AdHocSubProcessActivityActivationRecordValue>> wrappedStream) {
    return new AdHocSubProcessActivityActivationRecordStream(wrappedStream);
  }

  public AdHocSubProcessActivityActivationRecordStream withAdHocSubProcessInstanceKey(
      final String adHocSubProcessInstanceKey) {
    return valueFilter(
        record -> record.getAdHocSubProcessInstanceKey().equals(adHocSubProcessInstanceKey));
  }
}
