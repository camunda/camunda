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
package io.zeebe.broker.it.workflow;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.client.api.ZeebeFuture;
import io.zeebe.broker.client.api.clients.WorkflowClient;
import io.zeebe.broker.client.api.events.MessageEvent;
import io.zeebe.broker.client.api.events.WorkflowInstanceState;
import io.zeebe.broker.client.cmd.ClientCommandRejectedException;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class PublishMessageTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule();
  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule, "test", false);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("wf")
          .startEvent()
          .intermediateCatchEvent("catch-event")
          .message(c -> c.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .endEvent()
          .done();

  private WorkflowClient workflowClient;

  @Before
  public void init() {
    clientRule
        .getClient()
        .newCreateTopicCommand()
        .name("test")
        .partitions(10)
        .replicationFactor(1)
        .send()
        .join();

    clientRule.waitUntilTopicsExists("test");

    workflowClient = clientRule.getClient().topicClient("test").workflowClient();

    workflowClient.newDeployCommand().addWorkflowModel(WORKFLOW, "wf.bpmn").send().join();

    eventRecorder.startRecordingEvents();
  }

  @Test
  public void shouldCorrelateMessageToAllSubscriptions() {
    // given
    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-123\"}")
        .send()
        .join();

    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-123\"}")
        .send()
        .join();

    // when
    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .send()
        .join();

    // then
    waitUntil(
        () -> eventRecorder.getWorkflowInstanceEvents(WorkflowInstanceState.COMPLETED).size() == 2);
  }

  @Test
  public void shouldCorrelateMessageOnDifferentPartitions() {
    // given
    final MessageEvent message1 =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .send()
            .join();

    final MessageEvent message2 =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-456")
            .send()
            .join();

    assertThat(message1.getMetadata().getPartitionId())
        .isNotEqualTo(message2.getMetadata().getPartitionId());

    // when
    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-123\"}")
        .send()
        .join();

    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-456\"}")
        .send()
        .join();

    // then
    waitUntil(
        () -> eventRecorder.getWorkflowInstanceEvents(WorkflowInstanceState.COMPLETED).size() == 2);
  }

  @Test
  public void shouldRejectMessageWithSameId() {
    // given
    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .messageId("foo")
        .send()
        .join();

    // when
    final ZeebeFuture<MessageEvent> future =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .messageId("foo")
            .send();

    // then
    assertThatThrownBy(future::join)
        .isInstanceOf(ClientCommandRejectedException.class)
        .hasMessageContaining("message with id 'foo' is already published");
  }
}
