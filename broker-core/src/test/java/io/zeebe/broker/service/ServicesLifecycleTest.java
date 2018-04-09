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
package io.zeebe.broker.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutionException;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.Broker;
import io.zeebe.broker.clustering.ClusterServiceNames;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.WorkflowQueueServiceNames;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.raft.Raft;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;

public class ServicesLifecycleTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    @Ignore
    public void shouldShutdownStreamProcessingBeforeLogStream() throws Exception
    {
        // given
        final Broker broker = brokerRule.getBroker();
        final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();
        final String logStreamName = ClientApiRule.DEFAULT_TOPIC_NAME + "." + apiRule.getDefaultPartitionId();

        final ServiceName<StreamProcessorController> streamProcessorServiceName = WorkflowQueueServiceNames.workflowInstanceStreamProcessorServiceName(logStreamName);
        final ServiceName<Raft> raftServiceName = ClusterServiceNames.raftServiceName(logStreamName);

        final StreamProcessorController streamProcessorController =
                getService(serviceContainer, streamProcessorServiceName);

        // when
        serviceContainer.removeService(raftServiceName).get();

        // then
        assertThat(!streamProcessorController.isOpened()).isTrue();
        assertThat(streamProcessorController.isFailed()).isFalse();
    }

    protected <S> S getService(ServiceContainer serviceContainer, ServiceName<S> serviceName)
    {
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
}
