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
package io.zeebe.broker.system.deployment.service;

import io.zeebe.broker.system.deployment.handler.WorkflowRequestMessageHandler;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.*;

public class WorkflowRequestMessageHandlerService implements Service<WorkflowRequestMessageHandler>
{
    private WorkflowRequestMessageHandler handler;

    private final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
            .onAdd((name, stream) -> handler.addStream(stream))
            .onRemove((name, stream) -> handler.removeStream(stream))
            .build();


    @Override
    public void start(ServiceStartContext startContext)
    {
        handler = new WorkflowRequestMessageHandler();
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        // nothing to stop
    }

    @Override
    public WorkflowRequestMessageHandler get()
    {
        return handler;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

}
