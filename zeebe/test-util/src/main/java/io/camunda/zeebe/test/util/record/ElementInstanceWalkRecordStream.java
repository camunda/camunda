/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ElementInstanceWalkRecordValue;
import java.util.stream.Stream;

public class ElementInstanceWalkRecordStream
    extends ExporterRecordStream<ElementInstanceWalkRecordValue, ElementInstanceWalkRecordStream> {

  public ElementInstanceWalkRecordStream(
      final Stream<Record<ElementInstanceWalkRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected ElementInstanceWalkRecordStream supply(
      final Stream<Record<ElementInstanceWalkRecordValue>> wrappedStream) {
    return new ElementInstanceWalkRecordStream(wrappedStream);
  }

  public ElementInstanceWalkRecordStream withProcessInstanceKey(final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public ElementInstanceWalkRecordStream withElementInstanceKey(final long elementInstanceKey) {
    return valueFilter(v -> v.getElementInstanceKey() == elementInstanceKey);
  }

  public ElementInstanceWalkRecordStream withParentKey(final long parentKey) {
    return valueFilter(v -> v.getParentKey() == parentKey);
  }

  public ElementInstanceWalkRecordStream withIndex(final long index) {
    return valueFilter(v -> v.getIndex() == index);
  }
}
