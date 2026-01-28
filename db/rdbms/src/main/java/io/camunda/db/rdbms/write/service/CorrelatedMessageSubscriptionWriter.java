/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;

public class CorrelatedMessageSubscriptionWriter extends ProcessInstanceDependant
    implements RdbmsWriter {

  private final ExecutionQueue executionQueue;

  public CorrelatedMessageSubscriptionWriter(
      final ExecutionQueue executionQueue, final CorrelatedMessageSubscriptionMapper mapper) {
    super(mapper);
    this.executionQueue = executionQueue;
  }

  public void create(final CorrelatedMessageSubscriptionDbModel correlatedMessageSubscription) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.CORRELATED_MESSAGE_SUBSCRIPTION,
            WriteStatementType.INSERT,
            getCompositeId(correlatedMessageSubscription),
            "io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper.insert",
            correlatedMessageSubscription));
  }

  private static String getCompositeId(
      final CorrelatedMessageSubscriptionDbModel correlatedMessageSubscription) {
    return correlatedMessageSubscription.messageKey()
        + "_"
        + correlatedMessageSubscription.subscriptionKey();
  }
}
