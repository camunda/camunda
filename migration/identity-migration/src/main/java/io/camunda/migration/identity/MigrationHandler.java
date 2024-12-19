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
import java.util.concurrent.CompletionException;

public interface MigrationHandler {

  int SIZE = 100;

  void migrate();

  default boolean isConflictError(final Throwable e) {
    return (e instanceof final BrokerRejectionException rejectionException
            && rejectionException.getRejection().type() == RejectionType.ALREADY_EXISTS)
        || (e instanceof final CamundaBrokerException brokerException
            && isConflictError(brokerException.getCause()))
        || (e instanceof final CompletionException completionException
            && isConflictError(completionException.getCause()));
  }
}
