/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.MutableStorageOrdinal;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Marks a {@link TypedRecordProcessor} that handles the records of a storage ordinal controlled
 * entity, and declares the operations the engine needs from such a processor.
 *
 * <p>A storage ordinal controlled entity belongs to an instance (process/decision etc.), and every
 * record of the entity carries the storage ordinal of that instance (see {@link
 * StorageOrdinalRelated}). Consumers of the stream, for example the exporters, route a record by
 * this ordinal, so every record the engine writes for the entity must carry it: commands, events
 * and rejections.
 *
 * <p>Every processor registered for a value type whose record implements {@link
 * StorageOrdinalRelated} must implement this interface. The engine rejects the registration of a
 * processor that does not.
 *
 * @param <T> the record value type processed by the implementing {@link TypedRecordProcessor}
 */
public interface StorageOrdinalAware<T extends UnifiedRecordValue & MutableStorageOrdinal> {

  /**
   * Sets the given ordinal on the command value. The engine calls this before the banned instance
   * check, the suspension gate and {@link TypedRecordProcessor#processRecord}, for a command that
   * arrives without the ordinal, so every rejection and every follow-up record written from the
   * command value carries it. The type bound makes a processor for a record without {@link
   * MutableStorageOrdinal} a compile error.
   *
   * @param command the command being handled
   * @param storageOrdinal the ordinal of the target process instance, which carries its root's
   *     ordinal
   */
  default void restoreStorageOrdinal(final TypedRecord<T> command, final int storageOrdinal) {
    command.getValue().setStorageOrdinal(storageOrdinal);
  }
}
