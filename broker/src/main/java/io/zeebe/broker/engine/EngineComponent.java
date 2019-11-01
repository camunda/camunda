/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.engine;

import io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames;
import io.zeebe.broker.engine.impl.SubscriptionApiCommandMessageHandlerService;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.servicecontainer.ServiceContainer;

public class EngineComponent implements Component {

  @Override
  public void init(SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final SubscriptionApiCommandMessageHandlerService messageHandlerService =
        new SubscriptionApiCommandMessageHandlerService();
    serviceContainer
        .createService(
            EngineServiceNames.SUBSCRIPTION_API_MESSAGE_HANDLER_SERVICE_NAME, messageHandlerService)
        .dependency(
            ClusterBaseLayerServiceNames.ATOMIX_SERVICE, messageHandlerService.getAtomixInjector())
        .groupReference(
            ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME,
            messageHandlerService.getLeaderParitionsGroupReference())
        .install();
  }
}
