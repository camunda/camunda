/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor.writers;

import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.Intent;
import java.util.function.UnaryOperator;

/** Things that any actor can write to a partition. */
public interface TypedCommandWriter {

  void appendNewCommand(Intent intent, RecordValue value);

  void appendFollowUpCommand(long key, Intent intent, RecordValue value);

  /**
   * @deprecated The modifier parameter is used, but at the time of writing unnecessarily by {@link
   *     io.zeebe.engine.processing.job.JobTimeoutTrigger}
   */
  @Deprecated
  void appendFollowUpCommand(
      long key, Intent intent, RecordValue value, UnaryOperator<RecordMetadata> modifier);

  void reset();

  /** @return position of new record, negative value on failure */
  long flush();
}
