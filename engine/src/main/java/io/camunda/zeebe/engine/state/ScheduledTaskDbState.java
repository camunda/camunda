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
import io.camunda.zeebe.db.impl.rocksdb.BufferedMessagesMetrics;
import io.camunda.zeebe.engine.state.distribution.DbDistributionState;
import io.camunda.zeebe.engine.state.immutable.DistributionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.instance.DbTimerInstanceState;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

/** Contains read-only state that can be accessed safely by scheduled tasks. */
public final class ScheduledTaskDbState implements ScheduledTaskState {
  private final DistributionState distributionState;
  private final MessageState messageState;
  private final TimerInstanceState timerInstanceState;

  public ScheduledTaskDbState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final BufferedMessagesMetrics bufferedMessagesMetrics) {
    distributionState = new DbDistributionState(zeebeDb, transactionContext);
    messageState = new DbMessageState(zeebeDb, transactionContext, bufferedMessagesMetrics);
    timerInstanceState = new DbTimerInstanceState(zeebeDb, transactionContext);
  }

  @Override
  public DistributionState getDistributionState() {
    return distributionState;
  }

  @Override
  public MessageState getMessageState() {
    return messageState;
  }

  @Override
  public TimerInstanceState getTimerState() {
    return timerInstanceState;
  }
}
