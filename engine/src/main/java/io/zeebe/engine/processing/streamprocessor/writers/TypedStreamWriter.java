/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RejectionType;
import java.util.function.Consumer;

/** Things that only a stream processor should write to the log stream (+ commands) */
public interface TypedStreamWriter extends TypedCommandWriter, TypedEventWriter {
  void appendRejection(
      TypedRecord<? extends UnpackedObject> command, RejectionType type, String reason);

  void appendRejection(
      TypedRecord<? extends UnpackedObject> command,
      RejectionType type,
      String reason,
      Consumer<RecordMetadata> metadata);

  void configureSourceContext(long sourceRecordPosition);
}
