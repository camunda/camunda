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
package io.zeebe.broker.test;

import static io.zeebe.broker.task.TaskQueueServiceNames.taskQueueInstanceStreamProcessorServiceName;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_LOG_NAME;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

import io.zeebe.broker.Broker;
import io.zeebe.broker.TestLoggers;
import io.zeebe.broker.event.TopicSubscriptionServiceNames;
import io.zeebe.broker.system.SystemServiceNames;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.allocation.DirectBufferAllocator;

public class EmbeddedBrokerRule extends ExternalResource
{
    protected static final Logger LOG = TestLoggers.TEST_LOGGER;

    protected Broker broker;

    protected Supplier<InputStream> configSupplier;

    public EmbeddedBrokerRule()
    {
        this("zeebe.unit-test.cfg.toml");
    }

    public EmbeddedBrokerRule(Supplier<InputStream> configSupplier)
    {
        this.configSupplier = configSupplier;
    }

    public EmbeddedBrokerRule(String configFileClasspathLocation)
    {
        this(() -> EmbeddedBrokerRule.class.getClassLoader().getResourceAsStream(configFileClasspathLocation));
    }

    protected long startTime;
    @Override
    protected void before() throws Throwable
    {
        startTime = System.currentTimeMillis();
        startBroker();
        LOG.info("Broker startup time: " + (System.currentTimeMillis() - startTime));
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void after()
    {
        LOG.info("Test execution time: " + (System.currentTimeMillis() - startTime));
        startTime = System.currentTimeMillis();
        stopBroker();
        LOG.info("Broker closing time: " + (System.currentTimeMillis() - startTime));

        final long allocatedMemoryInKb = DirectBufferAllocator.getAllocatedMemoryInKb();
        if (allocatedMemoryInKb > 0)
        {
            LOG.warn("There are still allocated direct buffers of a total size of {}kB.", allocatedMemoryInKb);
        }
    }

    public Broker getBroker()
    {
        return this.broker;
    }

    public void restartBroker()
    {
        stopBroker();
        startBroker();
    }

    public void stopBroker()
    {
        broker.close();
        broker = null;
        System.gc();
    }

    public void startBroker()
    {
        try (InputStream configStream = configSupplier.get())
        {
            broker = new Broker(configStream);
        }
        catch (final IOException e)
        {
            throw new RuntimeException("Unable to open configuration", e);
        }

        final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();


        try
        {
            // Hack: block until default task queue log has been installed
            // How to make it better: https://github.com/zeebe-io/zeebe/issues/196
            serviceContainer.createService(TestService.NAME, new TestService())
                .dependency(TopicSubscriptionServiceNames.subscriptionManagementServiceName(DEFAULT_LOG_NAME))
                .dependency(SystemServiceNames.SYSTEM_PROCESSOR)
                .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME))
                .dependency(taskQueueInstanceStreamProcessorServiceName(DEFAULT_LOG_NAME))
                .install()
                .get(25, TimeUnit.SECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e)
        {
            stopBroker();
            throw new RuntimeException("Default task queue log not installed into the container withing 5 seconds.");
        }
    }

    public <T> void removeService(ServiceName<T> name)
    {
        try
        {
            broker.getBrokerContext().getServiceContainer()
                .removeService(name)
                .get(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e)
        {
            throw new RuntimeException("Could not remove service " + name.getName() + " in 10 seconds.");
        }
    }

    static class TestService implements Service<TestService>
    {

        static final ServiceName<TestService> NAME = ServiceName.newServiceName("testService", TestService.class);

        @Override
        public void start(ServiceStartContext startContext)
        {

        }

        @Override
        public void stop(ServiceStopContext stopContext)
        {

        }

        @Override
        public TestService get()
        {
            return this;
        }

    }

}
