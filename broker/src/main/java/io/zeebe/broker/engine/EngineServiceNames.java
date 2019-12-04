/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine;

import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandMessageHandler;
import io.zeebe.logstreams.state.SnapshotDeletionListener;
import io.zeebe.servicecontainer.ServiceName;

public final class EngineServiceNames {
  public static final ServiceName<SubscriptionCommandMessageHandler>
      SUBSCRIPTION_API_MESSAGE_HANDLER_SERVICE_NAME =
          ServiceName.newServiceName(
              "broker.subscriptionApi.messageHandler", SubscriptionCommandMessageHandler.class);

  private EngineServiceNames() {}

  public static ServiceName<SnapshotDeletionListener> logStreamDeletionService(
      final int partitionId) {
    return ServiceName.newServiceName(
        String.format("logstream.%d.deletion", partitionId), SnapshotDeletionListener.class);
  }
}
