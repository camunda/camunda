/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.StoredMessage;
import org.agrona.DirectBuffer;

public interface MessageState {

  boolean existMessageCorrelation(long messageKey, DirectBuffer bpmnProcessId);

  boolean existActiveProcessInstance(
      final String tenantId, DirectBuffer bpmnProcessId, DirectBuffer correlationKey);

  DirectBuffer getProcessInstanceCorrelationKey(long processInstanceKey);

  void visitMessages(
      final String tenantId,
      DirectBuffer name,
      DirectBuffer correlationKey,
      MessageVisitor visitor);

  StoredMessage getMessage(long messageKey);

  /**
   * Visits the messages with expired deadline, using the provided visitor. The visitor stops when
   * all messages with expired deadline have been visited, but can also be controlled through the
   * visitor function.
   *
   * @param timestamp Timestamp used to determine whether the deadline has expired
   * @param startAt Index used to start the iteration at; visiting starts at the beginning when
   *     startAt is {@code null}
   * @param visitor This method is called for each message with expired deadline. It must return a
   *     boolean that when {@code true} allows the visiting to continue, or when {@code false} stops
   *     the visiting.
   * @return {@code true} when the visiting is stopped due to the returned value of the last call to
   *     visitor, otherwise {@code false}
   */
  boolean visitMessagesWithDeadlineBeforeTimestamp(
      long timestamp, final Index startAt, ExpiredMessageVisitor visitor);

  boolean exist(
      DirectBuffer name,
      DirectBuffer correlationKey,
      DirectBuffer messageId,
      final String tenantId);

  /**
   * Visits every cross-partition message-start lock entry on this partition. Each entry marks a
   * local process-correlation-key lock whose holder instance was created on another partition via
   * the cross-partition message-start handshake. The pull-based release loop iterates these to
   * learn which holder instances to poll {@code P_B} for.
   *
   * <p>The {@code bpmnProcessId} and {@code correlationKey} buffers are only valid for the duration
   * of the callback; copy them if they must outlive it.
   *
   * @param visitor invoked for each lock entry
   */
  void visitCrossPartitionStartLocks(CrossPartitionStartLockVisitor visitor);

  /**
   * Index to point to a specific position in the messages with deadline column family.
   *
   * @param key The message key
   * @param deadline The deadline of the message
   */
  record Index(long key, long deadline) {}

  @FunctionalInterface
  interface MessageVisitor {
    boolean visit(StoredMessage message);
  }

  @FunctionalInterface
  interface ExpiredMessageVisitor {
    boolean visit(final long deadline, long messageKey);
  }

  @FunctionalInterface
  interface CrossPartitionStartLockVisitor {
    void visit(
        DirectBuffer bpmnProcessId,
        DirectBuffer correlationKey,
        long holderProcessInstanceKey,
        String tenantId);
  }
}
