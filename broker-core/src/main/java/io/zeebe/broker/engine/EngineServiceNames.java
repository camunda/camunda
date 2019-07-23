/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine;

import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandMessageHandler;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.logstreams.impl.delete.DeletionService;
import io.zeebe.servicecontainer.ServiceName;

public class EngineServiceNames {
  public static final ServiceName<EngineService> ENGINE_SERVICE_NAME =
      ServiceName.newServiceName("logstreams.processor", EngineService.class);
  public static final ServiceName<SubscriptionCommandMessageHandler>
      SUBSCRIPTION_API_MESSAGE_HANDLER_SERVICE_NAME =
          ServiceName.newServiceName(
              "broker.subscriptionApi.messageHandler", SubscriptionCommandMessageHandler.class);

  public static final ServiceName<StateStorageFactory> stateStorageFactoryServiceName(
      String partitionName) {
    return ServiceName.newServiceName(
        String.format("%s.rocksdb.storage", partitionName), StateStorageFactory.class);
  }

  public static final ServiceName<DeletionService> leaderLogStreamDeletionService(int partitionId) {
    return ServiceName.newServiceName(
        String.format("logstream.%d.deletion", partitionId), DeletionService.class);
  }
}
