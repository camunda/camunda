/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

/**
 * The outcome of one {@link InboxDrainer#drainOnce()}.
 *
 * @param claimed how many messages the store handed out
 * @param written how many of them the writer wrote, in order from the oldest
 * @param failed true if the store or the writer threw an exception. The messages that were not
 *     acknowledged will be delivered again.
 */
public record DrainResult(int claimed, int written, boolean failed) {

  /** True if the inbox had no message and nothing went wrong. */
  public boolean wasIdle() {
    return claimed == 0 && !failed;
  }
}
