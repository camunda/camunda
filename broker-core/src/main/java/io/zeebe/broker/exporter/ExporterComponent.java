/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.exporter;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.exporter.ExporterServiceNames.EXPORTER_MANAGER;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.servicecontainer.ServiceContainer;

public class ExporterComponent implements Component {

  @Override
  public void init(SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final ExporterManagerService exporterManagerService =
        new ExporterManagerService(context.getBrokerConfiguration());

    serviceContainer
        .createService(EXPORTER_MANAGER, exporterManagerService)
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, exporterManagerService.getPartitionsGroupReference())
        .install();
  }
}
