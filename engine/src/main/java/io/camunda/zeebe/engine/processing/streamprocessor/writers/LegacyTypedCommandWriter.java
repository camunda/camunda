/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

/** Things that any actor can write to a partition. */
/** This interface is supposed to be replaced by RestrictedTypedCommandWriter */
public interface LegacyTypedCommandWriter extends TypedCommandWriter {

  @Override
  void appendNewCommand(Intent intent, RecordValue value);

  @Override
  void appendFollowUpCommand(long key, Intent intent, RecordValue value);

  void reset();

  /**
   * @return position of new record, negative value on failure
   */
  long flush();
}
