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
package io.zeebe.broker.it.subscription;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.record.*;
import io.zeebe.client.api.subscription.TopicSubscription;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.*;
import org.junit.rules.*;

public class ManagementSubscriptionTest {
  private static final WorkflowDefinition WORKFLOW =
      Bpmn.createExecutableWorkflow("wf").startEvent().done();

  public static final String SUBSCRIPTION_NAME = "foo";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public Timeout timeout = Timeout.seconds(30);

  protected ZeebeClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient();

    final String defaultTopic = clientRule.getClient().getConfiguration().getDefaultTopic();
    clientRule.waitUntilTopicsExists(defaultTopic);
  }

  @Test
  public void shouldOpenSubscription() {
    // when
    final TopicSubscription subscription =
        client.newManagementSubscription().name(SUBSCRIPTION_NAME).recordHandler(r -> {}).open();

    // then
    assertThat(subscription.isOpen());
  }

  @Test
  public void shouldReceiveDeploymentEvents() {
    // given
    final DeploymentEvent deploymentEvent =
        client
            .topicClient()
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "wf.bpmn")
            .send()
            .join();

    // when
    final List<DeploymentEvent> events = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .deploymentEventHandler(events::add)
        .open();

    waitUntil(() -> events.size() >= 1);

    // then
    assertThat(events).hasSize(1);

    final DeploymentEvent event = events.get(0);
    assertThat(event.getKey()).isEqualTo(deploymentEvent.getKey());
    assertThat(event.getState()).isEqualTo(DeploymentState.CREATED);
    assertThat(event.getDeploymentTopic()).isEqualTo(clientRule.getDefaultTopic());

    assertThat(event.getDeployedWorkflows()).hasSize(1);
    final Workflow deployedWorkflow = event.getDeployedWorkflows().get(0);
    assertThat(deployedWorkflow.getBpmnProcessId()).isEqualTo("wf");
    assertThat(deployedWorkflow.getVersion()).isEqualTo(1);
    assertThat(deployedWorkflow.getWorkflowKey()).isEqualTo(1L);
    assertThat(deployedWorkflow.getResourceName()).isEqualTo("wf.bpmn");

    assertThat(event.getResources()).hasSize(1);
    final DeploymentResource deploymentResource = event.getResources().get(0);
    assertThat(deploymentResource.getResourceName()).isEqualTo("wf.bpmn");
    assertThat(deploymentResource.getResourceType()).isEqualTo(ResourceType.BPMN_XML);
    assertThat(deploymentResource.getResource())
        .isEqualTo(Bpmn.convertToString(WORKFLOW).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldReceiveDeploymentCommand() {
    // given
    final DeploymentEvent deploymentEvent =
        client
            .topicClient()
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "wf.bpmn")
            .send()
            .join();

    // when
    final List<DeploymentCommand> commands = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .deploymentCommandHandler(commands::add)
        .open();

    waitUntil(() -> commands.size() >= 1);

    // then
    assertThat(commands).hasSize(1);

    final DeploymentCommand command = commands.get(0);
    assertThat(command.getKey()).isEqualTo(deploymentEvent.getKey());
    assertThat(command.getName()).isEqualTo(DeploymentCommandName.CREATE);
    assertThat(command.getDeploymentTopic()).isEqualTo(clientRule.getDefaultTopic());

    assertThat(command.getResources()).hasSize(1);
    final DeploymentResource deploymentResource = command.getResources().get(0);
    assertThat(deploymentResource.getResourceName()).isEqualTo("wf.bpmn");
    assertThat(deploymentResource.getResourceType()).isEqualTo(ResourceType.BPMN_XML);
    assertThat(deploymentResource.getResource())
        .isEqualTo(Bpmn.convertToString(WORKFLOW).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldReceiveTopicEvents() {
    // given
    final List<TopicEvent> events = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .topicEventHandler(events::add)
        .startAtTailOfTopic()
        .open();

    // when
    final TopicEvent topicEvent =
        client
            .newCreateTopicCommand()
            .name("my-topic")
            .partitions(1)
            .replicationFactor(1)
            .send()
            .join();

    waitUntil(() -> events.size() >= 3);

    // then
    assertThat(events).hasSize(3);

    assertThat(events)
        .extracting(TopicEvent::getState)
        .containsExactly(TopicState.CREATING, TopicState.CREATE_COMPLETE, TopicState.CREATED);
    assertThat(events).extracting(TopicEvent::getName).containsOnly("my-topic");
    assertThat(events).extracting(TopicEvent::getKey).containsOnly(topicEvent.getKey());
    assertThat(events).extracting(TopicEvent::getPartitions).containsOnly(1);
    assertThat(events).extracting(TopicEvent::getReplicationFactor).containsOnly(1);

    assertThat(events.get(2).getPartitionIds()).containsExactly(2);
  }

  @Test
  public void shouldReceiveTopicCommands() {
    // given
    final List<TopicCommand> commands = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .topicCommandHandler(commands::add)
        .startAtTailOfTopic()
        .open();

    // when
    final TopicEvent topicEvent =
        client
            .newCreateTopicCommand()
            .name("my-topic")
            .partitions(1)
            .replicationFactor(1)
            .send()
            .join();

    waitUntil(() -> commands.size() >= 1);

    // then
    assertThat(commands).hasSize(1);

    final TopicCommand command = commands.get(0);
    assertThat(command.getCommandName()).isEqualTo(TopicCommandName.CREATE);
    assertThat(command.getName()).isEqualTo("my-topic");
    assertThat(command.getKey()).isEqualTo(topicEvent.getKey());
    assertThat(command.getPartitions()).isEqualTo(1);
    assertThat(command.getReplicationFactor()).isEqualTo(1);
  }

  @Test
  public void shouldReceiveAllRecords() {
    // given
    final List<Record> records = new ArrayList<>();
    client
        .newManagementSubscription()
        .name(SUBSCRIPTION_NAME)
        .recordHandler(records::add)
        .startAtTailOfTopic()
        .open();

    // when
    client
        .newCreateTopicCommand()
        .name("my-topic")
        .partitions(1)
        .replicationFactor(1)
        .send()
        .join();

    client
        .topicClient("my-topic")
        .workflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW, "wf.bpmn")
        .send()
        .join();

    waitUntil(() -> records.size() >= 6);

    // then
    assertThat(records).hasSize(6);
    assertThat(records)
        .extracting(r -> r.getMetadata().getValueType())
        .containsOnly(ValueType.DEPLOYMENT, ValueType.TOPIC);
    assertThat(records)
        .extracting(r -> r.getMetadata().getRecordType())
        .containsOnly(RecordType.EVENT, RecordType.COMMAND);
  }
}
