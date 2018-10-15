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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MessageCorrelationTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  private static final BpmnModelInstance CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess("wf")
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .sequenceFlowId("to-end")
          .endEvent()
          .done();

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess("wf")
          .startEvent()
          .receiveTask("receive-message")
          .message(m -> m.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .sequenceFlowId("to-end")
          .endEvent()
          .done();

  @Parameter(0)
  public String elementType;

  @Parameter(1)
  public BpmnModelInstance workflow;

  @Parameters(name = "{0}")
  public static final Object[][] parameters() {
    return new Object[][] {
      {"intermediate message catch event", CATCH_EVENT_WORKFLOW},
      {"receive task", RECEIVE_TASK_WORKFLOW}
    };
  }

  @Before
  public void init() {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(workflow, "wf.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldCorrelateMessageIfEnteredBefore() {
    // given
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-123\"}")
        .send()
        .join();

    assertElementActivated("receive-message");

    // when
    clientRule
        .getWorkflowClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .send()
        .join();

    // then
    assertWorkflowInstanceCompleted("wf");
  }

  @Test
  public void shouldCorrelateMessageIfPublishedBefore() {
    // given
    clientRule
        .getWorkflowClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .send()
        .join();

    // when
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-123\"}")
        .send()
        .join();

    // then
    assertWorkflowInstanceCompleted("wf");
  }

  @Test
  public void shouldCorrelateMessageAndMergePayload() {
    // given
    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("wf")
        .latestVersion()
        .payload("{\"orderId\":\"order-123\"}")
        .send()
        .join();

    // when
    clientRule
        .getWorkflowClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .payload(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    assertWorkflowInstanceCompleted("wf");

    assertElementCompleted(
        "wf",
        "receive-message",
        (catchEventOccurredEvent) ->
            assertThat(catchEventOccurredEvent.getPayloadAsMap())
                .containsExactly(entry("orderId", "order-123"), entry("foo", "bar")));
  }
}
