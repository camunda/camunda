/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.partitionapi;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;

/**
 * Takes an inter-partition message that came out of the mailbox and writes its command to the log
 * of the partition.
 */
interface MailboxCommandHandler {

  /**
   * Handles the message after the messages that were handed over before, in the same order as
   * messages from the network.
   *
   * @param sender who sent the message, for log messages
   * @param message the encoded message, as {@link InterPartitionCommandSenderImpl} produces it
   * @return a future that completes with true if the command is written to the log, and with false
   *     if it was dropped or the log did not take it. It fails if handling threw an exception.
   */
  ActorFuture<Boolean> handleMailboxMessage(MemberId sender, byte[] message);
}
