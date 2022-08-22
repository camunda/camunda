/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;

/** Things that only a stream processor should write to the log stream (+ commands) */
public interface LegacyTypedStreamWriter extends TypedEventWriter {

  void appendFollowUpCommand(long key, Intent intent, RecordValue value);

  void appendRecord(
      long key,
      RecordType type,
      Intent intent,
      RejectionType rejectionType,
      String rejectionReason,
      RecordValue value);

  void configureSourceContext(long sourceRecordPosition);

  void reset();

  long flush();
}
