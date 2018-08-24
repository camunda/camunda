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
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.DeploymentCommand;
import io.zeebe.gateway.api.commands.DeploymentCommandName;
import io.zeebe.gateway.api.commands.DeploymentResource;
import io.zeebe.gateway.api.commands.ResourceType;
import io.zeebe.gateway.api.commands.Workflow;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.api.events.DeploymentState;
import io.zeebe.gateway.api.record.Record;
import io.zeebe.gateway.api.record.RecordType;
import io.zeebe.gateway.api.record.ValueType;
import io.zeebe.gateway.api.subscription.TopicSubscription;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class ManagementSubscriptionTest {
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("wf").startEvent().done();

  public static final String SUBSCRIPTION_NAME = "foo";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Rule public Timeout timeout = Timeout.seconds(30);

  protected ZeebeClient client;

  @Before
  public void setUp() {
    this.client = clientRule.getClient();

    final String topic = client.getConfiguration().getDefaultTopic();
    clientRule.waitUntilTopicsExists(topic);
  }

  @Test
  public void shouldOpenSubscription() {
    // when
    final TopicSubscription subscription =
        client.newManagementSubscription().name(SUBSCRIPTION_NAME).recordHandler(r -> {}).open();

    // then
    assertThat(subscription.isOpen()).isTrue();
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

    waitUntil(() -> events.size() >= 2);

    // then
    assertThat(events).hasSize(2);

    assertThat(events)
        .extracting(DeploymentEvent::getState)
        .contains(DeploymentState.CREATED, DeploymentState.DISTRIBUTE);
    for (DeploymentEvent event : events) {
      assertThat(event.getKey()).isEqualTo(deploymentEvent.getKey());
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

    waitUntil(() -> commands.size() >= 2);

    // then
    assertThat(commands).hasSize(2);

    assertThat(commands)
        .extracting(DeploymentCommand::getName)
        .contains(DeploymentCommandName.CREATE, DeploymentCommandName.CREATING);
    assertThat(commands).extracting(Record::getKey).contains(-1L, deploymentEvent.getKey());

    for (DeploymentCommand command : commands) {
      assertThat(command.getDeploymentTopic()).isEqualTo(clientRule.getDefaultTopic());

      assertThat(command.getResources()).hasSize(1);
      final DeploymentResource deploymentResource = command.getResources().get(0);
      assertThat(deploymentResource.getResourceName()).isEqualTo("wf.bpmn");
      assertThat(deploymentResource.getResourceType()).isEqualTo(ResourceType.BPMN_XML);
      assertThat(deploymentResource.getResource())
          .isEqualTo(Bpmn.convertToString(WORKFLOW).getBytes(StandardCharsets.UTF_8));
    }
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
        .topicClient()
        .workflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW, "wf.bpmn")
        .send()
        .join();

    waitUntil(() -> records.size() >= 4);

    // then
    assertThat(records).hasSize(4);
    assertThat(records)
        .extracting(r -> r.getMetadata().getValueType())
        .containsOnly(ValueType.DEPLOYMENT);
    assertThat(records)
        .extracting(r -> r.getMetadata().getRecordType())
        .containsOnly(RecordType.EVENT, RecordType.COMMAND);
  }
}
