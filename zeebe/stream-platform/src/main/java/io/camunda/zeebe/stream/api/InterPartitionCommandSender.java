/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;

/**
 * Supports sending arbitrary commands to another partition. Sending may be unreliable and fail
 * silently, it is up to the caller to detect this and retry.
 */
public interface InterPartitionCommandSender {
  void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final UnifiedRecordValue command);

  /**
   * Uses the given record key when writing the command. Otherwise, behaves like {@link
   * InterPartitionCommandSender#sendCommand}. This may be used in cases where the key has been
   * provided to the users, and the entity must be available on the receiving partition with that
   * specific key. This also helps inform the receiving partition that this command was sent from
   * another partition (and from which partition as the key encodes the partition id).
   *
   * @param recordKey Record key to use when writing the command. Ignored if null.
   */
  void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue command);

  default void sendCommand(
      final int receiverPartitionId,
      final ValueType valueType,
      final Intent intent,
      final Long recordKey,
      final UnifiedRecordValue command,
      final AuthInfo authInfo) {
    sendCommand(receiverPartitionId, valueType, intent, command);
  }
}
