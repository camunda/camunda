/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.message.DbMessageState;

/** Contains read-only state that can be accessed safely by scheduled tasks. */
public final class ScheduledTaskDbState {
  private final MessageState messageState;

  public ScheduledTaskDbState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    messageState = new DbMessageState(zeebeDb, transactionContext);
  }

  public MessageState getMessageState() {
    return messageState;
  }
}
