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
package io.zeebe.broker;

import io.zeebe.broker.clustering.ClusterComponent;
import io.zeebe.broker.logstreams.LogStreamsComponent;
import io.zeebe.broker.system.ConfigurationManager;
import io.zeebe.broker.system.SystemComponent;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.task.TaskQueueComponent;
import io.zeebe.broker.transport.TransportComponent;
import io.zeebe.broker.workflow.WorkflowComponent;
import io.zeebe.util.LogUtil;
import io.zeebe.util.sched.clock.ActorClock;
import org.slf4j.Logger;

import java.io.InputStream;

public class Broker implements AutoCloseable
{
    public static final Logger LOG = Loggers.SYSTEM_LOGGER;

    public static final String VERSION;

    static
    {
        final String version = Broker.class.getPackage().getImplementationVersion();
        VERSION = version != null ? version : "development";
    }

    protected final SystemContext brokerContext;
    protected boolean isClosed = false;

    public Broker(String[] args)
    {
        this(new SystemContext(args, null));
    }

    public Broker(String configFileLocation, ActorClock clock)
    {
        this(new SystemContext(configFileLocation, clock));
    }

    public Broker(InputStream configStream)
    {
        this(new SystemContext(configStream, null));
    }

    public Broker(InputStream configStream, ActorClock clock)
    {
        this(new SystemContext(configStream, clock));
    }

    public Broker(ConfigurationManager configurationManager)
    {
        this(new SystemContext(configurationManager, null));
    }

    public Broker(SystemContext brokerContext)
    {
        this.brokerContext = brokerContext;
        LogUtil.doWithMDC(brokerContext.getDiagnosticContext(), () -> start());
    }

    protected void start()
    {
        LOG.info("Version: {}", VERSION);

        brokerContext.addComponent(new SystemComponent());
        brokerContext.addComponent(new TransportComponent());
        brokerContext.addComponent(new LogStreamsComponent());
        brokerContext.addComponent(new TaskQueueComponent());
        brokerContext.addComponent(new WorkflowComponent());
        brokerContext.addComponent(new ClusterComponent());

        brokerContext.init();
    }

    @Override
    public void close()
    {
        LogUtil.doWithMDC(brokerContext.getDiagnosticContext(), () ->
        {
            if (!isClosed)
            {
                brokerContext.close();
                isClosed = true;
                LOG.info("Broker closed");
            }
        });
    }

    public SystemContext getBrokerContext()
    {
        return brokerContext;
    }
}
