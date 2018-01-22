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
package io.zeebe.broker.it.clustering;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.clustering.impl.TopicLeader;
import io.zeebe.client.event.DeploymentEvent;
import io.zeebe.client.event.Event;
import io.zeebe.gossip.Loggers;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.test.util.AutoCloseableRule;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

public class DeploymentClusteredTest
{
    private static final int PARTITION_COUNT = 5;

    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .endEvent()
            .done();

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public ClientRule clientRule = new ClientRule(false);

    @Rule
    public Timeout timeout = new Timeout(15, TimeUnit.SECONDS);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ZeebeClient client;

    private List<Broker> brokers;

    @Before
    public void init()
    {
        client = clientRule.getClient();

        brokers = new ArrayList<>();
        brokers.add(startBroker("zeebe.cluster.1.cfg.toml"));
        brokers.add(startBroker("zeebe.cluster.2.cfg.toml"));
        brokers.add(startBroker("zeebe.cluster.3.cfg.toml"));

        // wait until cluster is ready
        doRepeatedly(() -> client.requestTopology().execute())
            .until(topology -> topology.getBrokers().size() == 3 && topology.getTopicLeaders().size() == 1);
    }

    @Test
    public void shouldDeployWorkflowAndCreateInstances()
    {
        // given
        client.topics().create("test", PARTITION_COUNT).execute();

        waitUntil(() -> getLeadersOfTopic("test") >= 3);

        // when
        client.workflows().deploy("test")
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        // then
        for (int p = 0; p < PARTITION_COUNT; p++)
        {
            client.workflows().create("test")
                .bpmnProcessId("process")
                .execute();
        }
    }

    @Test
    public void shouldRejectDeployment()
    {
        // given
        client.topics().create("test", PARTITION_COUNT).execute();

        waitUntil(() -> getLeadersOfTopic("test") >= 3);

        // when
        brokers.get(2).close();

        // then
        assertThatThrownBy(() ->
        {
            client.workflows().deploy("test")
                .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                .execute();
        }).hasMessageContaining("Deployment was rejected");
    }

    @Test
    public void shouldDeployOnRemainingBrokers() throws InterruptedException
    {
        // given
        client.topics().create("test", PARTITION_COUNT).execute();

        waitUntil(() -> getLeadersOfTopic("test") >= 3);

        // when
        brokers.get(2).close();
        Thread.sleep(3000); // new leader is chosen for partition

        doRepeatedly(() -> client.requestTopology()
                                 .execute()
                                 .getTopicLeaders()).until(leaders -> leaders != null && leaders.size() >= PARTITION_COUNT);

        // then
        final DeploymentEvent deploymentEvent = client.workflows()
                                           .deploy("test")
                                           .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                                           .execute();

        assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
        assertThat(deploymentEvent.getErrorMessage()).isEmpty();
    }

    @Test
    @Ignore
    public void shouldDeleteWorkflowsIfRejected() throws Exception
    {
        // given
        client.topics().create("test", PARTITION_COUNT).execute();
        waitUntil(() -> getLeadersOfTopic("test") >= 3);

        doRepeatedly(() -> client.topics()
                                 .getTopics()
                                 .execute())
            .until((topics -> topics.getTopics().size() == 1));

        final List<String> workflowStates = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        client.topics().newSubscription("test")
              .name("test")
              .startAtHeadOfTopic()
              .handler((event -> {
                  events.add(event);
              }))
              .workflowEventHandler(e -> workflowStates.add(e.getState()))
              .open();

        // when
        brokers.get(2).close();

        // then
        assertThatThrownBy(() -> client.workflows().deploy("test")
                                       .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                                       .execute())
            .hasMessageContaining("Deployment was rejected");

        waitUntil(() -> {
            final long deleted = workflowStates.stream()
                                               .filter(s -> s.equals("DELETED"))
                                               .count();
            Loggers.GOSSIP_LOGGER.debug("Deleted count {}", deleted);
            return deleted == PARTITION_COUNT;

        });
    }

    @Test
    @Ignore
    public void shouldFailToCreateWorkflowInstanceIfRejected()
    {
        // given
        client.topics().create("test", PARTITION_COUNT).execute();
        waitUntil(() -> getLeadersOfTopic("test") >= 3);

        // when
        brokers.get(2).close();

        // then
        assertThatThrownBy(() ->
        {
            client.workflows().deploy("test")
                .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                .execute();
        }).hasMessageContaining("Deployment was rejected");

        for (int p = 0; p < PARTITION_COUNT; p++)
        {
            assertThatThrownBy(() ->
            {
                client.workflows().create("test")
                    .bpmnProcessId("process")
                    .execute();
            }).hasMessageContaining("Failed to create instance of workflow");
        }
    }

    @Ignore
    @Test
    public void shouldResetWorkflowVersionsIfRejected() throws InterruptedException
    {
        // given
        client.topics().create("test", PARTITION_COUNT).execute();
        waitUntil(() -> getLeadersOfTopic("test") >= 3);

        // when
        brokers.get(2).close();

        // then
        assertThatThrownBy(() ->
        {
            client.workflows().deploy("test")
                .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                .execute();
        }).hasMessageContaining("Deployment was rejected");

        final DeploymentEvent deploymentEvent = client.workflows().deploy("test")
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        final int version = deploymentEvent.getDeployedWorkflows().get(0).getVersion();
        assertThat(version).isEqualTo(1);
    }

    private long getLeadersOfTopic(final String topic)
    {
        final List<TopicLeader> topicLeaders = clientRule.getClient().requestTopology()
            .execute()
            .getTopicLeaders();

        return topicLeaders.stream()
                .filter(t -> topic.equals(t.getTopicName()))
                .map(t -> t.getSocketAddress().toString())
                .distinct()
                .count();
    }

    private Broker startBroker(String configFile)
    {
        final InputStream config = this.getClass().getClassLoader().getResourceAsStream(configFile);
        final Broker broker = new Broker(config);
        closeables.manage(broker);

        return broker;
    }
}
