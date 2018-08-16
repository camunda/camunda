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

import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.gateway.api.ZeebeFuture;
import io.zeebe.gateway.api.clients.WorkflowClient;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.api.events.MessageEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceState;
import io.zeebe.gateway.cmd.ClientCommandRejectedException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class PublishMessageTest {

  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule("zeebe.unit-test.increased.partitions.cfg.toml");
  public ClientRule clientRule = new ClientRule(brokerRule);
  public TopicEventRecorder eventRecorder =
      new TopicEventRecorder(clientRule, DEFAULT_TOPIC, false);

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

    clientRule.waitUntilTopicsExists(DEFAULT_TOPIC);

    workflowClient = clientRule.getClient().topicClient().workflowClient();

    final DeploymentEvent deploymentEvent =
        workflowClient.newDeployCommand().addWorkflowModel(WORKFLOW, "wf.bpmn").send().join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());

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
        () ->
            eventRecorder.getElementsInState("wf", WorkflowInstanceState.ELEMENT_COMPLETED).size()
                == 2);
  }

  @Test
  public void shouldCorrelateMessageWithZeroTTL() {
    // given
    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-123\"}")
        .send()
        .join();

    waitUntil(
        () ->
            eventRecorder.hasElementInState(
                "catch-event", WorkflowInstanceState.ELEMENT_ACTIVATED));

    // when
    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .timeToLive(Duration.ZERO)
        .send()
        .join();

    // then
    waitUntil(
        () ->
            eventRecorder.hasElementInState(
                "catch-event", WorkflowInstanceState.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotCorrelateMessageAfterTTL() {
    // given
    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .timeToLive(Duration.ZERO)
        .payload(Collections.singletonMap("msg", "failure"))
        .send()
        .join();

    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .timeToLive(Duration.ofMinutes(1))
        .payload(Collections.singletonMap("msg", "expected"))
        .send()
        .join();

    // when
    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-123\"}")
        .send()
        .join();

    // then
    waitUntil(
        () ->
            eventRecorder.hasElementInState(
                "catch-event", WorkflowInstanceState.ELEMENT_COMPLETED));

    final WorkflowInstanceEvent catchEventOccurred =
        eventRecorder.getElementInState("catch-event", WorkflowInstanceState.ELEMENT_COMPLETED);
    assertThat(catchEventOccurred.getPayloadAsMap()).contains(entry("msg", "expected"));
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
            .correlationKey("order-124")
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
        .payload("{\"orderId\":\"order-124\"}")
        .send()
        .join();

    // then
    waitUntil(
        () ->
            eventRecorder.getElementsInState("wf", WorkflowInstanceState.ELEMENT_COMPLETED).size()
                == 2);
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
