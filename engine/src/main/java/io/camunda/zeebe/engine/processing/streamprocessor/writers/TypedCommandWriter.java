/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.Intent;

/** Things that any actor can write to a partition. */
public interface TypedCommandWriter {

  void appendNewCommand(Intent intent, RecordValue value);

  void appendFollowUpCommand(long key, Intent intent, RecordValue value);

  void reset();

  /** @return position of new record, negative value on failure */
  long flush();
}
