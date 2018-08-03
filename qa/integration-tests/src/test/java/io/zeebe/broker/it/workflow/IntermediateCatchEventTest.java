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
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.client.api.events.WorkflowInstanceEvent;
import io.zeebe.broker.client.api.events.WorkflowInstanceState;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IntermediateCatchEventTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule();
  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  private static final WorkflowDefinition WORKFLOW =
      Bpmn.createExecutableWorkflow("wf")
          .startEvent()
          .intermediateCatchEvent(
              "catch-event", c -> c.messageName("order canceled").correlationKey("$.orderId"))
          .sequenceFlow()
          .endEvent()
          .done();

  @Before
  public void init() {
    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW, "wf.bpmn")
        .send()
        .join();
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

    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.CATCH_EVENT_ENTERED));

    // when
    clientRule
        .getWorkflowClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .send()
        .join();

    // then
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));
  }

  @Test
  public void shouldCorrelateMessageIfPublischedBefore() {
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
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));
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
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));

    final WorkflowInstanceEvent catchEventOccurredEvent =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.CATCH_EVENT_OCCURRED);
    assertThat(catchEventOccurredEvent.getPayloadAsMap())
        .containsExactly(entry("orderId", "order-123"), entry("foo", "bar"));
  }
}
