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
package io.zeebe.broker.logstreams;

import io.zeebe.broker.logstreams.cfg.SnapshotStorageCfg;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.servicecontainer.*;

public class SnapshotStorageService implements Service<SnapshotStorage>
{
    protected SnapshotStorageCfg config;
    private SnapshotStorage snapshotStorage;

    public SnapshotStorageService(ConfigurationManager configurationManager)
    {
        config = configurationManager.readEntry("snapshot", SnapshotStorageCfg.class);
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        serviceContext.run(() ->
        {
            final String snapshotDirectory = config.directory;

            snapshotStorage = LogStreams.createFsSnapshotStore(snapshotDirectory)
                .build();
        });
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        // nothing to do
    }

    @Override
    public SnapshotStorage get()
    {
        return snapshotStorage;
    }

}
