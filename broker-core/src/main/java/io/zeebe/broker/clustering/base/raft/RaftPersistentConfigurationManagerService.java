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
import io.zeebe.servicecontainer.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class RaftPersistentConfigurationManagerService
    implements Service<RaftPersistentConfigurationManager> {
  private RaftPersistentConfigurationManager service;
  private BrokerCfg configuration;

  public RaftPersistentConfigurationManagerService(BrokerCfg configuration) {
    this.configuration = configuration;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final DataCfg dataConfiguration = configuration.getData();

    final String[] directories = dataConfiguration.getDirectories();

    for (String directory : directories) {
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

    service = new RaftPersistentConfigurationManager(configuration.getData());

    startContext.async(startContext.getScheduler().submitActor(service));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(service.close());
  }

  @Override
  public RaftPersistentConfigurationManager get() {
    return service;
  }
}
