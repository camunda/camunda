/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/** The two kinds of messages that a partition inbox receives. */
public enum MessageKind {
  /** A request from a gateway. The sender expects a response in the outbox. */
  CLIENT_REQUEST,
  /** A command from another partition. The sender expects no response. */
  INTER_PARTITION_COMMAND
}
