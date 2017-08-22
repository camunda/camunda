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

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.servicecontainer.ServiceName;

public class LogStreamServiceNames
{

    public static final ServiceName<LogStreamsManager> LOG_STREAMS_MANAGER_SERVICE = ServiceName.newServiceName("logstreams.manager", LogStreamsManager.class);
    public static final ServiceName<SnapshotStorage> SNAPSHOT_STORAGE_SERVICE = ServiceName.newServiceName("snapshot.storage", SnapshotStorage.class);
    public static final ServiceName<LogStream> WORKFLOW_STREAM_GROUP = ServiceName.newServiceName("logstreams.worfklow", LogStream.class);
    public static final ServiceName<LogStream> SYSTEM_STREAM_GROUP = ServiceName.newServiceName("logstreams.system", LogStream.class);

    public static final ServiceName<LogStream> logStreamServiceName(String logName)
    {
        return ServiceName.newServiceName(String.format("log.%s", logName), LogStream.class);
    }

}
