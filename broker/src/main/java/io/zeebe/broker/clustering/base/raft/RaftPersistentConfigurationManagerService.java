/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.raft;

import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.distributedlog.StorageConfigurationManager;
import io.zeebe.distributedlog.impl.LogstreamConfig;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class RaftPersistentConfigurationManagerService
    implements Service<StorageConfigurationManager> {
  private final BrokerCfg configuration;
  private StorageConfigurationManager service;

  public RaftPersistentConfigurationManagerService(BrokerCfg configuration) {
    this.configuration = configuration;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final DataCfg dataConfiguration = configuration.getData();

    for (String directory : dataConfiguration.getDirectories()) {
      final File configDirectory = new File(directory);

      if (!configDirectory.exists()) {
        try {
          configDirectory.getParentFile().mkdirs();
          Files.createDirectory(configDirectory.toPath());
        } catch (final IOException e) {
          throw new RuntimeException("Unable to create directory " + configDirectory, e);
        }
      }
    }

    service =
        new StorageConfigurationManager(
            configuration.getData().getDirectories(), configuration.getData().getLogSegmentSize());

    /* A temp solution so that DistributedLogstream primitive can create logs in this directory */
    LogstreamConfig.putConfig(String.valueOf(configuration.getCluster().getNodeId()), service);

    startContext.async(startContext.getScheduler().submitActor(service));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(service.close());
  }

  @Override
  public StorageConfigurationManager get() {
    return service;
  }
}
