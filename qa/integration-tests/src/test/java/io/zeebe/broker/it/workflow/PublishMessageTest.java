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

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertElementActivated;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertElementCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class PublishMessageTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("wf")
          .startEvent()
          .intermediateCatchEvent("catch-event")
          .message(c -> c.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .endEvent()
          .done();
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  private WorkflowClient workflowClient;

  @Before
  public void init() {

    workflowClient = clientRule.getClient().workflowClient();

    final DeploymentEvent deploymentEvent =
        workflowClient.newDeployCommand().addWorkflowModel(WORKFLOW, "wf.bpmn").send().join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldCorrelateMessageToAllSubscriptions() {
    // given
    final WorkflowInstanceEvent wf =
        workflowClient
            .newCreateInstanceCommand()
            .bpmnProcessId("wf")
            .latestVersion()
            .payload("{\"orderId\":\"order-123\"}")
            .send()
            .join();

    final WorkflowInstanceEvent wf2 =
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
    assertWorkflowInstanceCompleted("wf", wf.getWorkflowInstanceKey());
    assertWorkflowInstanceCompleted("wf", wf2.getWorkflowInstanceKey());
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

    assertElementActivated("catch-event");

    // when
    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .timeToLive(Duration.ZERO)
        .send()
        .join();

    // then
    assertElementCompleted("wf", "catch-event");
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

    assertElementCompleted(
        "wf",
        "catch-event",
        (catchEventOccurred) ->
            assertThat(catchEventOccurred.getPayloadAsMap()).contains(entry("msg", "expected")));
  }

  @Test
  public void shouldCorrelateMessageOnDifferentPartitions() {
    // given
    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .send()
        .join();

    workflowClient
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-124")
        .send()
        .join();

    // when
    final WorkflowInstanceEvent wf =
        workflowClient
            .newCreateInstanceCommand()
            .bpmnProcessId("wf")
            .latestVersion()
            .payload("{\"orderId\":\"order-123\"}")
            .send()
            .join();

    final WorkflowInstanceEvent wf2 =
        workflowClient
            .newCreateInstanceCommand()
            .bpmnProcessId("wf")
            .latestVersion()
            .payload("{\"orderId\":\"order-124\"}")
            .send()
            .join();

    // then
    assertWorkflowInstanceCompleted("wf", wf.getWorkflowInstanceKey());
    assertWorkflowInstanceCompleted("wf", wf2.getWorkflowInstanceKey());
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
    final ZeebeFuture<Void> future =
        workflowClient
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .messageId("foo")
            .send();

    // then
    assertThatThrownBy(future::join)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("message with id 'foo' is already published");
  }
}
