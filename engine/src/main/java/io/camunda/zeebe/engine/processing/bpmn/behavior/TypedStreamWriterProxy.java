/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;

public final class TypedStreamWriterProxy implements TypedStreamWriter {

  private TypedStreamWriter writer;

  public void wrap(final TypedStreamWriter writer) {
    this.writer = writer;
  }

  @Override
  public void appendRejection(
      final TypedRecord<? extends RecordValue> command,
      final RejectionType type,
      final String reason) {
    writer.appendRejection(command, type, reason);
  }

  @Override
  public void configureSourceContext(final long sourceRecordPosition) {
    writer.configureSourceContext(sourceRecordPosition);
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    writer.appendFollowUpEvent(key, intent, value);
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return writer.canWriteEventOfLength(eventLength);
  }

  @Override
  public int getMaxEventLength() {
    return writer.getMaxEventLength();
  }

  @Override
  public void appendNewCommand(final Intent intent, final RecordValue value) {
    writer.appendNewCommand(intent, value);
  }

  @Override
  public void appendFollowUpCommand(final long key, final Intent intent, final RecordValue value) {
    writer.appendFollowUpCommand(key, intent, value);
  }

  @Override
  public void reset() {
    writer.reset();
  }

  @Override
  public long flush() {
    return writer.flush();
  }
}
