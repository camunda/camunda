/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Some commands are distributed to different partitions. During distribution the command gets
 * written on the other partitions. Depending on whether it is distributed the behavior of the
 * processor may slightly change. For example, if it was distributed before we don't want to
 * distribute it a second time.
 *
 * <p>This interface provides some convenience for commands that get distributed. Instead of
 * checking if the command was distributed in the processor directly, the interface takes care of
 * it.
 *
 * @param <T>
 */
public interface DistributedTypedRecordProcessor<T extends UnifiedRecordValue>
    extends TypedRecordProcessor<T> {

  @Override
  default void processRecord(final TypedRecord<T> command) {
    if (command.isCommandDistributed()) {
      processDistributedCommand(command);
    } else {
      processNewCommand(command);
    }
  }

  /**
   * Process a command that is not distributed yet
   *
   * @param command the not yet distributed command to process
   */
  void processNewCommand(final TypedRecord<T> command);

  /**
   * Process a command that has been distributed. Be aware to not distribute it again!
   *
   * @param command the already distributed command to process
   */
  void processDistributedCommand(final TypedRecord<T> command);
}
