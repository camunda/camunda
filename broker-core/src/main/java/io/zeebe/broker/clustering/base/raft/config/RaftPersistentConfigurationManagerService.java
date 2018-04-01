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
package io.zeebe.broker.clustering.base.raft.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import io.zeebe.broker.logstreams.cfg.LogStreamsCfg;
import io.zeebe.broker.transport.cfg.TransportComponentCfg;
import io.zeebe.servicecontainer.*;

public class RaftPersistentConfigurationManagerService implements Service<RaftPersistentConfigurationManager>
{
    private final TransportComponentCfg config;
    private final LogStreamsCfg logStreamsCfg;
    private RaftPersistentConfigurationManager service;

    public RaftPersistentConfigurationManagerService(TransportComponentCfg config, LogStreamsCfg logStreamsCfg)
    {
        this.config = config;
        this.logStreamsCfg = logStreamsCfg;
    }

    @Override
    public void start(ServiceStartContext startContext)
    {
        final File configDirectory = new File(config.management.getDirectory());

        if (!configDirectory.exists())
        {
            try
            {
                configDirectory.getParentFile().mkdirs();
                Files.createDirectory(configDirectory.toPath());
            }
            catch (final IOException e)
            {
                throw new RuntimeException("Unable to create directory " + configDirectory, e);
            }
        }

        service = new RaftPersistentConfigurationManager(config.management.getDirectory(), logStreamsCfg);

        startContext.async(startContext.getScheduler().submitActor(service));
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(service.close());
    }

    @Override
    public RaftPersistentConfigurationManager get()
    {
        return service;
    }

}
