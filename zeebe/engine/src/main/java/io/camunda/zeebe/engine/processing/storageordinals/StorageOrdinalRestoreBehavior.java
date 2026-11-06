/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import io.camunda.zeebe.engine.processing.streamprocessor.InstanceKeyResolver.InstanceKeys;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;

/**
 * Restores the storage ordinal on a command that does not carry it, before the engine checks bans
 * and suspension, so every rejection and every record written from the command value carries it.
 *
 * <p>The restore does not depend on the archiverless mode: an instance keeps the ordinal it was
 * created with, so its rejections must carry it after the mode is turned off as well. With the mode
 * off from the start the lookup confirms {@link StorageOrdinalRelated#MAIN_INDEX}.
 *
 * <p>Every element instance of an instance carries the instance's ordinal. The gateway keys process
 * instance commands (cancel, suspend, resume) by the instance and does not set the instance key on
 * the value, so the instance key resolver cannot place them. For such a process instance command,
 * the ordinal is read from the element instance with the command key.
 *
 * <p>A command can name an instance of another partition, for example a message subscription
 * command on the message partition. The element instance of such a key is never in this partition's
 * state, so the read is skipped when the partition id encoded in the key is not this partition.
 */
@NullMarked
public final class StorageOrdinalRestoreBehavior {

  private final int partitionId;
  private final ElementInstanceState elementInstanceState;

  public StorageOrdinalRestoreBehavior(
      final int partitionId, final ElementInstanceState elementInstanceState) {
    this.partitionId = partitionId;
    this.elementInstanceState = elementInstanceState;
  }

  /**
   * True when the command needs the ordinal restored: its processor is {@link StorageOrdinalAware}
   * and the value carries no ordinal ({@link StorageOrdinalRelated#MAIN_INDEX}). The caller then
   * resolves the instance keys and calls {@link #restore}.
   */
  public boolean appliesTo(final TypedRecord<?> command, final TypedRecordProcessor<?> processor) {
    return processor instanceof StorageOrdinalAware<?> && carriesNoOrdinal(command);
  }

  /**
   * Sets the ordinal of the target process instance on the command value through the processor's
   * {@link StorageOrdinalAware} hook, given the keys the caller resolved. Does nothing when the
   * command does not {@link #appliesTo apply}, when the keys and the command key give no element
   * instance, when the key belongs to another partition, or when the element instance is not in
   * state.
   */
  public void restore(
      final TypedRecord<?> command,
      final TypedRecordProcessor<?> processor,
      final InstanceKeys keys) {
    if (!appliesTo(command, processor)) {
      return;
    }

    final StorageOrdinalAware<?> aware = (StorageOrdinalAware<?>) processor;
    final long elementInstanceKey = elementInstanceKeyFor(keys, command);
    if (elementInstanceKey <= 0) {
      return;
    }
    if (Protocol.decodePartitionId(elementInstanceKey) != partitionId) {
      return;
    }
    final var instance = elementInstanceState.getInstance(elementInstanceKey);
    if (instance == null) {
      return;
    }
    restore(aware, command, instance.getValue().getStorageOrdinal());
  }

  private static long elementInstanceKeyFor(final InstanceKeys keys, final TypedRecord<?> command) {
    if (keys.isResolved()) {
      return keys.processInstanceKey();
    }
    if (command.getKey() > 0 && command.getValue() instanceof ProcessInstanceRecord) {
      return command.getKey();
    }
    return -1;
  }

  /**
   * Client commands arrive with the msgpack default 0, because the ordinal is not part of the
   * client API. Engine-written commands copy the ordinal when they are written, so the value check
   * skips them and the restore does not depend on request metadata. A command of a legacy instance
   * (main index) is looked up and set to 0 again, which is harmless.
   */
  private static boolean carriesNoOrdinal(final TypedRecord<?> command) {
    return command.getValue() instanceof final StorageOrdinalRelated related
        && related.getStorageOrdinal() == StorageOrdinalRelated.MAIN_INDEX;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void restore(
      final StorageOrdinalAware<?> aware, final TypedRecord<?> command, final int storageOrdinal) {
    ((StorageOrdinalAware) aware).restoreStorageOrdinal(command, storageOrdinal);
  }
}
