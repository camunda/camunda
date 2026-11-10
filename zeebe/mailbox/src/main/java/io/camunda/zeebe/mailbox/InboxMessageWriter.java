/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import io.camunda.zeebe.mailbox.api.InboxMessage;
import java.util.List;

/**
 * Where an {@link InboxDrainer} puts the messages it took from the inbox. In the broker this is the
 * log of the partition.
 */
@FunctionalInterface
public interface InboxMessageWriter {

  /**
   * Writes the messages in the order of the list, and returns when the written messages are
   * durable. Blocking is allowed. A message must not be written if an earlier one of the list was
   * not.
   *
   * @param messages the messages to write, oldest first
   * @return how many messages from the start of the list were written. Less than the size of the
   *     list if the log cannot take more now, for example because of backpressure.
   * @throws RuntimeException if the outcome is unknown. The messages may or may not be written.
   */
  int writeAll(List<InboxMessage> messages);
}
