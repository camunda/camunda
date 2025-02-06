/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MigrationHandler<T> {
  static final int SIZE = 100;
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  protected boolean isConflictError(final Throwable e) {
    return (e instanceof final BrokerRejectionException rejectionException
            && rejectionException.getRejection().type() == RejectionType.ALREADY_EXISTS)
        || (e instanceof final CamundaBrokerException brokerException
            && isConflictError(brokerException.getCause()))
        || (e instanceof final CompletionException completionException
            && isConflictError(completionException.getCause()));
  }

  public void migrate() {
    logger.info("Migrating started.");
    List<T> batch;
    do {
      batch = fetchBatch();
      process(batch);
    } while (!batch.isEmpty());
    logger.info("Migrating finished.");
  }

  protected abstract List<T> fetchBatch();

  protected abstract void process(List<T> batch);
}
