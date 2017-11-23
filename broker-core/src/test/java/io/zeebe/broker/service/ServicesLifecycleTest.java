package io.zeebe.broker.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.Broker;
import io.zeebe.broker.clustering.ClusterServiceNames;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.workflow.WorkflowQueueServiceNames;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.raft.Raft;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;

public class ServicesLifecycleTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    @Test
    public void shouldShutdownStreamProcessingBeforeLogStream() throws Exception
    {
        // given
        final Broker broker = brokerRule.getBroker();
        final ServiceContainerImpl serviceContainer = (ServiceContainerImpl) broker.getBrokerContext().getServiceContainer();
        final String logStreamName = ClientApiRule.DEFAULT_TOPIC_NAME + "." + apiRule.getDefaultPartitionId();

        final ServiceName<StreamProcessorController> streamProcessorServiceName = WorkflowQueueServiceNames.workflowInstanceStreamProcessorServiceName(logStreamName);
        final ServiceName<Raft> raftServiceName = ClusterServiceNames.raftServiceName(logStreamName);

        final StreamProcessorController streamProcessorController = serviceContainer.<StreamProcessorController>getService(streamProcessorServiceName).get();

        // when
        serviceContainer.removeService(raftServiceName).get();

        // then
        assertThat(streamProcessorController.isClosed()).isTrue();
        assertThat(streamProcessorController.isFailed()).isFalse();
    }
}
