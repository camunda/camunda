/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.Intent;

public final class NoopTypedStreamWriter implements TypedStreamWriter {

  @Override
  public void appendRejection(
      final TypedRecord<? extends RecordValue> command,
      final RejectionType type,
      final String reason) {
    // no op implementation
  }

  @Override
  public void configureSourceContext(final long sourceRecordPosition) {
    // no op implementation
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    // no op implementation
  }

  @Override
  public void appendNewCommand(final Intent intent, final RecordValue value) {
    // no op implementation
  }

  @Override
  public void appendFollowUpCommand(final long key, final Intent intent, final RecordValue value) {
    // no op implementation
  }

  @Override
  public void reset() {
    // no op implementation
  }

  @Override
  public long flush() {
    return 0;
  }
}
