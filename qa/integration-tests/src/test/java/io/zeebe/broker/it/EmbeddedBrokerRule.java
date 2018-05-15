/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

import io.zeebe.broker.Broker;
import io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.FileUtil;
import io.zeebe.util.allocation.DirectBufferAllocator;
import io.zeebe.util.sched.clock.ControlledActorClock;

public class EmbeddedBrokerRule extends ExternalResource
{
    static final ServiceName<Object> AWAIT_BROKER_SERVICE_NAME = ServiceName.newServiceName("testService", Object.class);

    protected static final Logger LOG = TestLoggers.TEST_LOGGER;

    protected Broker broker;

    protected ControlledActorClock controlledActorClock = new ControlledActorClock();

    protected Supplier<InputStream> configSupplier;

    public EmbeddedBrokerRule()
    {
        this(() -> EmbeddedBrokerRule.class.getResourceAsStream("/zeebe.default.cfg.toml"));
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

    private File brokerBase;

    @Override
    protected void before()
    {
        startTime = System.currentTimeMillis();
        startBroker();
        LOG.info("\n====\nBroker startup time: {}\n====\n", (System.currentTimeMillis() - startTime));
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

        try
        {
            FileUtil.deleteFolder(brokerBase.getAbsolutePath());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    public Broker getBroker()
    {
        return this.broker;
    }

    public ControlledActorClock getClock()
    {
        return controlledActorClock;
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
        if (brokerBase == null)
        {
            brokerBase = Files.newTemporaryFolder();
        }

        try (InputStream configStream = configSupplier.get())
        {
            broker = new Broker(configStream, brokerBase.getAbsolutePath(), controlledActorClock);
        }
        catch (final IOException e)
        {
            throw new RuntimeException("Unable to open configuration", e);
        }

        final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();

        try
        {
            // Hack: block until the system stream processor is available
            // this is required in the broker-test suite, because the client rule does not perform request retries
            // How to make it better: https://github.com/zeebe-io/zeebe/issues/196
            final String systemTopicName = Protocol.SYSTEM_TOPIC + "-" + Protocol.SYSTEM_PARTITION;

            serviceContainer.createService(AWAIT_BROKER_SERVICE_NAME, new NoneService())
                .dependency(ClusterBaseLayerServiceNames.leaderPartitionServiceName(systemTopicName))
                .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME))
                .install()
                .get(25, TimeUnit.SECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e)
        {
            stopBroker();
            throw new RuntimeException("System patition not installed into the container withing 25 seconds.");
        }
    }

    public <S> S getService(ServiceName<S> serviceName)
    {
        final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();

        final Injector<S> injector = new Injector<>();

        final ServiceName<Object> accessorServiceName = ServiceName.newServiceName("serviceAccess" + serviceName.getName(), Object.class);
        try
        {
            serviceContainer
                .createService(accessorServiceName, new NoneService())
                .dependency(serviceName, injector)
                .install()
                .get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new RuntimeException(e);
        }

        serviceContainer.removeService(accessorServiceName);

        return injector.getValue();
    }

    protected class NoneService implements Service<Object>
    {
        @Override
        public void start(ServiceStartContext startContext)
        {
        }

        @Override
        public void stop(ServiceStopContext stopContext)
        {
        }

        @Override
        public Object get()
        {
            return null;
        }

    }

    public File getBrokerBase()
    {
        return brokerBase;
    }
}
