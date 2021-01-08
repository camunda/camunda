/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.Consumer;

/** Things that any actor can write to a partition. */
public interface TypedCommandWriter {

  void appendNewCommand(Intent intent, UnifiedRecordValue value);

  void appendFollowUpCommand(long key, Intent intent, UnifiedRecordValue value);

  void appendFollowUpCommand(
      long key, Intent intent, UnifiedRecordValue value, Consumer<RecordMetadata> metadata);

  void reset();

  /** @return position of new record, negative value on failure */
  long flush();
}
