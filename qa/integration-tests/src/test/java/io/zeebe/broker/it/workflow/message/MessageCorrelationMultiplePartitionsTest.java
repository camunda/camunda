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

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Collections;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MessageCorrelationMultiplePartitionsTest {

  private static final String CORRELATION_KEY_PARTITION_0 = "item-2";
  private static final String CORRELATION_KEY_PARTITION_1 = "item-1";
  private static final String CORRELATION_KEY_PARTITION_2 = "item-0";

  private static final String PROCESS_ID = "process";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent()
          .message(m -> m.name("message").zeebeCorrelationKey("$.key"))
          .endEvent("end")
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
  public void shouldOpenMessageSubscriptionsOnDifferentPartitions() {
    // when
    IntStream.range(0, 10)
        .forEach(
            i -> {
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_0));
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_1));
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_2));
            });

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED)
                .limit(30))
        .extracting(r -> tuple(r.getMetadata().getPartitionId(), r.getValue().getCorrelationKey()))
        .containsOnly(
            tuple(START_PARTITION_ID, CORRELATION_KEY_PARTITION_0),
            tuple(START_PARTITION_ID + 1, CORRELATION_KEY_PARTITION_1),
            tuple(START_PARTITION_ID + 2, CORRELATION_KEY_PARTITION_2));
  }

  @Test
  public void shouldPublishMessageOnDifferentPartitions() {
    // when
    IntStream.range(0, 10)
        .forEach(
            i -> {
              publishMessage(CORRELATION_KEY_PARTITION_0, Collections.singletonMap("p", "p0"));
              publishMessage(CORRELATION_KEY_PARTITION_1, Collections.singletonMap("p", "p1"));
              publishMessage(CORRELATION_KEY_PARTITION_2, Collections.singletonMap("p", "p2"));
            });

    // then
    assertThat(RecordingExporter.messageRecords(MessageIntent.PUBLISHED).limit(30))
        .extracting(r -> tuple(r.getMetadata().getPartitionId(), r.getValue().getCorrelationKey()))
        .containsOnly(
            tuple(START_PARTITION_ID, CORRELATION_KEY_PARTITION_0),
            tuple(START_PARTITION_ID + 1, CORRELATION_KEY_PARTITION_1),
            tuple(START_PARTITION_ID + 2, CORRELATION_KEY_PARTITION_2));
  }

  @Test
  public void shouldCorrelateMessageOnDifferentPartitions() {
    // given
    publishMessage(CORRELATION_KEY_PARTITION_0, Collections.singletonMap("p", "p0"));
    publishMessage(CORRELATION_KEY_PARTITION_1, Collections.singletonMap("p", "p1"));
    publishMessage(CORRELATION_KEY_PARTITION_2, Collections.singletonMap("p", "p2"));

    // when
    createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_0));
    createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_1));
    createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_2));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("end")
                .limit(3))
        .extracting(r -> r.getValue().getPayloadAsMap().get("p"))
        .contains("p0", "p1", "p2");
  }

  @Test
  public void shouldOpenMessageSubscriptionsOnSamePartitionsAfterRestart() {
    // given
    IntStream.range(0, 5)
        .forEach(
            i -> {
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_0));
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_1));
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_2));
            });

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED)
                .limit(15)
                .exists())
        .isTrue();

    // when
    brokerRule.stopBroker();
    brokerRule.startBroker();

    IntStream.range(0, 5)
        .forEach(
            i -> {
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_0));
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_1));
              createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_2));
            });

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED)
                .limit(30))
        .extracting(r -> tuple(r.getMetadata().getPartitionId(), r.getValue().getCorrelationKey()))
        .containsOnly(
            tuple(START_PARTITION_ID, CORRELATION_KEY_PARTITION_0),
            tuple(START_PARTITION_ID + 1, CORRELATION_KEY_PARTITION_1),
            tuple(START_PARTITION_ID + 2, CORRELATION_KEY_PARTITION_2));
  }

  private void createWorkflowInstance(Object payload) {
    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .payload(payload)
        .send()
        .join();
  }

  private void publishMessage(String correlationKey, Object payload) {
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("message")
        .correlationKey(correlationKey)
        .payload(payload)
        .send()
        .join();
  }
}
