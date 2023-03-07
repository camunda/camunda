/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.StoredMessage;
import org.agrona.DirectBuffer;

public interface MessageState {

  boolean existMessageCorrelation(long messageKey, DirectBuffer bpmnProcessId);

  boolean existActiveProcessInstance(DirectBuffer bpmnProcessId, DirectBuffer correlationKey);

  DirectBuffer getProcessInstanceCorrelationKey(long processInstanceKey);

  void visitMessages(DirectBuffer name, DirectBuffer correlationKey, MessageVisitor visitor);

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

  boolean exist(DirectBuffer name, DirectBuffer correlationKey, DirectBuffer messageId);

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
}
