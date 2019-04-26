/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
  private StorageConfigurationManager service;
  private BrokerCfg configuration;

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
            configuration.getData().getDirectories(),
            configuration.getData().getDefaultLogSegmentSize(),
            configuration.getData().getIndexBlockSize());

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
