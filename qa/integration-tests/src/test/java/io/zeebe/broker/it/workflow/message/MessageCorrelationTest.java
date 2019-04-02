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
package io.zeebe.broker.it.workflow.message;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstances;
import java.time.Duration;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MessageCorrelationTest {

  private static final String PROCESS_ID = "process";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("catch-event")
          .message(c -> c.name("order canceled").zeebeCorrelationKey("orderId"))
          .endEvent()
          .done();

  @Before
  public void init() {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "wf.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldCorrelateMessage() {
    // given
    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .variables(Collections.singletonMap("orderId", "order-123"))
        .send()
        .join();

    // when
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .variables(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    assertWorkflowInstanceCompleted(PROCESS_ID);

    final Record<WorkflowInstanceRecordValue> workflowInstanceEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId("catch-event")
            .getFirst();

    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords().withName("foo").getFirst();
    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(workflowInstanceEvent.getValue().getWorkflowInstanceKey());
  }

  @Test
  public void shouldCorrelateMessageWithZeroTTL() {
    // given
    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .variables(Collections.singletonMap("orderId", "order-123"))
        .send()
        .join();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .withMessageName("order canceled")
                .exists())
        .isTrue();

    // when
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .timeToLive(Duration.ZERO)
        .send()
        .join();

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("catch-event")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotCorrelateMessageAfterTTL() {
    // given
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .timeToLive(Duration.ZERO)
        .variables(Collections.singletonMap("msg", "failure"))
        .send()
        .join();

    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .timeToLive(Duration.ofMinutes(1))
        .variables(Collections.singletonMap("msg", "expected"))
        .send()
        .join();

    // when
    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .variables(Collections.singletonMap("orderId", "order-123"))
        .send()
        .join();

    // then
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId("catch-event")
            .getFirst();
    assertThat(
            WorkflowInstances.getCurrentVariables(
                record.getValue().getWorkflowInstanceKey(), record.getPosition()))
        .contains(entry("msg", "\"expected\""));
  }

  @Test
  public void shouldRejectMessageWithSameId() {
    // given
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("order canceled")
        .correlationKey("order-123")
        .messageId("foo")
        .send()
        .join();

    // when
    final ZeebeFuture<Void> future =
        clientRule
            .getClient()
            .newPublishMessageCommand()
            .messageName("order canceled")
            .correlationKey("order-123")
            .messageId("foo")
            .send();

    // then
    assertThatThrownBy(future::join)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Expected to publish a new message with id 'foo', but a message with that id was already published");
  }
}
