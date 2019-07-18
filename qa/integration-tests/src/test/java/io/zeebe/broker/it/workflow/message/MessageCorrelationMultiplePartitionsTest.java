/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.workflow.message;

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstances;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent()
          .message(m -> m.name("message").zeebeCorrelationKey("key"))
          .endEvent("end")
          .done();
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

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
        .extracting(r -> tuple(r.getPartitionId(), r.getValue().getCorrelationKey()))
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
        .extracting(r -> tuple(r.getPartitionId(), r.getValue().getCorrelationKey()))
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
    final long wfiKey1 =
        createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_0));
    final long wfiKey2 =
        createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_1));
    final long wfiKey3 =
        createWorkflowInstance(Collections.singletonMap("key", CORRELATION_KEY_PARTITION_2));

    // then
    final List<String> correlatedValues =
        Arrays.asList(
            WorkflowInstances.getCurrentVariables(wfiKey1).get("p"),
            WorkflowInstances.getCurrentVariables(wfiKey2).get("p"),
            WorkflowInstances.getCurrentVariables(wfiKey3).get("p"));

    assertThat(correlatedValues).contains("\"p0\"", "\"p1\"", "\"p2\"");
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
    RecordingExporter.reset();
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
        .extracting(r -> tuple(r.getPartitionId(), r.getValue().getCorrelationKey()))
        .containsOnly(
            tuple(START_PARTITION_ID, CORRELATION_KEY_PARTITION_0),
            tuple(START_PARTITION_ID + 1, CORRELATION_KEY_PARTITION_1),
            tuple(START_PARTITION_ID + 2, CORRELATION_KEY_PARTITION_2));
  }

  private long createWorkflowInstance(Object variables) {
    return clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getWorkflowInstanceKey();
  }

  private void publishMessage(String correlationKey, Object variables) {
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName("message")
        .correlationKey(correlationKey)
        .variables(variables)
        .send()
        .join();
  }
}
