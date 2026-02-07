/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.data.MapEntry.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient.ProcessInstanceCreationClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractEndEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractUserTaskBuilder;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.ProcessInstances;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class MessageCorrelationMultiplePartitionsTest {

  private static final Map<Integer, String> CORRELATION_KEYS =
      Maps.of(
          entry(START_PARTITION_ID, "item-2"),
          entry(START_PARTITION_ID + 1, "item-1"),
          entry(START_PARTITION_ID + 2, "item-0"));

  private static final Map<Integer, String> TENANT_IDS =
      Maps.of(
          entry(START_PARTITION_ID, "foo"),
          entry(START_PARTITION_ID + 1, "bar"),
          entry(START_PARTITION_ID + 2, "baz"));

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
          .endEvent("end")
          .done();

  @Rule public final EngineRule engine = EngineRule.multiplePartition(3);

  @Before
  public void init() {
    for (int i = 0; i < 3; i++) {
      assertThat(getPartitionId(CORRELATION_KEYS.get(START_PARTITION_ID + i)))
          .isEqualTo(START_PARTITION_ID + i);

      engine
          .deployment()
          .withXmlResource(PROCESS)
          .withTenantId(TENANT_IDS.get(START_PARTITION_ID + i))
          .deploy();
    }
  }

  @Test
  public void shouldOpenMessageSubscriptionsOnDifferentPartitions() {
    // when
    IntStream.range(0, 10)
        .forEach(
            i -> {
              final ProcessInstanceCreationClient processInstanceCreationClient =
                  engine.processInstance().ofBpmnProcessId(PROCESS_ID);
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID))
                  .create();
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 1))
                  .create();
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 2))
                  .create();
            });

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .limit(30))
        .extracting(
            r ->
                tuple(
                    r.getPartitionId(),
                    r.getValue().getCorrelationKey(),
                    r.getValue().getTenantId()))
        .containsOnly(
            tuple(
                START_PARTITION_ID,
                CORRELATION_KEYS.get(START_PARTITION_ID),
                TENANT_IDS.get(START_PARTITION_ID)),
            tuple(
                START_PARTITION_ID + 1,
                CORRELATION_KEYS.get(START_PARTITION_ID + 1),
                TENANT_IDS.get(START_PARTITION_ID + 1)),
            tuple(
                START_PARTITION_ID + 2,
                CORRELATION_KEYS.get(START_PARTITION_ID + 2),
                TENANT_IDS.get(START_PARTITION_ID + 2)));
  }

  @Test
  public void shouldCorrelateMessageOnDifferentPartitions() {
    // given
    engine.forEachPartition(
        partitionId ->
            engine
                .message()
                .onPartition(partitionId)
                .withName("message")
                .withCorrelationKey(CORRELATION_KEYS.get(partitionId))
                .withVariables(asMsgPack("p", "p" + partitionId))
                .withTenantId(TENANT_IDS.get(partitionId))
                .publish());

    // when
    final ProcessInstanceCreationClient processInstanceCreationClient =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID);
    final long processInstanceKey1 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
            .withTenantId(TENANT_IDS.get(START_PARTITION_ID))
            .create();
    final long processInstanceKey2 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
            .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 1))
            .create();
    final long processInstanceKey3 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
            .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 2))
            .create();

    // then
    final List<String> correlatedValues =
        Arrays.asList(
            ProcessInstances.getCurrentVariables(processInstanceKey1).get("p"),
            ProcessInstances.getCurrentVariables(processInstanceKey2).get("p"),
            ProcessInstances.getCurrentVariables(processInstanceKey3).get("p"));

    assertThat(correlatedValues).contains("\"p1\"", "\"p2\"", "\"p3\"");
  }

  @Test
  public void shouldOpenMessageSubscriptionsOnSamePartitionsAfterRestart() {
    // given
    final ProcessInstanceCreationClient processInstanceCreationClient =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID);

    IntStream.range(0, 5)
        .forEach(
            i -> {
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID))
                  .create();
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 1))
                  .create();
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 2))
                  .create();
            });

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .limit(15)
                .count())
        .isEqualTo(15);

    // when
    engine.stop();
    RecordingExporter.reset();
    engine.start();

    IntStream.range(0, 5)
        .forEach(
            i -> {
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID))
                  .create();
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 1))
                  .create();
              processInstanceCreationClient
                  .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
                  .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 2))
                  .create();
            });

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .limit(30))
        .extracting(
            r ->
                tuple(
                    r.getPartitionId(),
                    r.getValue().getCorrelationKey(),
                    r.getValue().getTenantId()))
        .hasSize(30)
        .containsOnly(
            tuple(
                START_PARTITION_ID,
                CORRELATION_KEYS.get(START_PARTITION_ID),
                TENANT_IDS.get(START_PARTITION_ID)),
            tuple(
                START_PARTITION_ID + 1,
                CORRELATION_KEYS.get(START_PARTITION_ID + 1),
                TENANT_IDS.get(START_PARTITION_ID + 1)),
            tuple(
                START_PARTITION_ID + 2,
                CORRELATION_KEYS.get(START_PARTITION_ID + 2),
                TENANT_IDS.get(START_PARTITION_ID + 2)));
  }

  @Test
  public void shouldCorrelateUsingMessageCorrelationOnDifferentPartitions() {
    // given
    final ProcessInstanceCreationClient processInstanceCreationClient =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID);
    final long processInstanceKey1 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
            .withTenantId(TENANT_IDS.get(START_PARTITION_ID))
            .create();
    final long processInstanceKey2 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
            .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 1))
            .create();
    final long processInstanceKey3 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
            .withTenantId(TENANT_IDS.get(START_PARTITION_ID + 2))
            .create();

    // when
    engine.forEachPartition(
        partitionId ->
            engine
                .messageCorrelation()
                .onPartition(partitionId)
                .withName("message")
                .withCorrelationKey(CORRELATION_KEYS.get(partitionId))
                .withVariables(asMsgPack("p", "p" + partitionId))
                .withTenantId(TENANT_IDS.get(partitionId))
                .correlate());

    // then
    final List<String> correlatedValues =
        Arrays.asList(
            ProcessInstances.getCurrentVariables(processInstanceKey1).get("p"),
            ProcessInstances.getCurrentVariables(processInstanceKey2).get("p"),
            ProcessInstances.getCurrentVariables(processInstanceKey3).get("p"));

    assertThat(correlatedValues).contains("\"p1\"", "\"p2\"", "\"p3\"");
  }

  @Test
  public void shouldCorrelateMessagesIdempotent() {
    final var processId = Strings.newRandomValidBpmnId();
    final var messageName = "event_message";
    final var correlationKey = CORRELATION_KEYS.get(START_PARTITION_ID);
    final var eventSubProcessStartId = "eventSubProcessStart";

    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .eventSubProcess(
                    "subprocess",
                    s ->
                        s.startEvent(eventSubProcessStartId)
                            .interrupting(false)
                            .message(
                                m ->
                                    m.name(messageName)
                                        .zeebeCorrelationKeyExpression("correlationKey"))
                            .userTask()
                            .endEvent())
                .startEvent()
                .userTask("wait", AbstractUserTaskBuilder::zeebeUserTask)
                .endEvent("terminate_instance", AbstractEndEventBuilder::terminate)
                .done())
        .deploy();
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("correlationKey", correlationKey)
            .onPartition(2)
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    engine.pauseProcessing(2);

    // when
    for (int i = 0; i < 10; i++) {
      engine
          .message()
          .withName(messageName)
          .withCorrelationKey(correlationKey)
          .withTimeToLive(Duration.ofMinutes(30))
          .onPartition(1)
          .publish();
    }

    // increase the time to retry the inter-partition correlation several times
    engine.increaseTime(PendingMessageSubscriptionCheckScheduler.SUBSCRIPTION_CHECK_INTERVAL);
    engine.increaseTime(PendingMessageSubscriptionCheckScheduler.SUBSCRIPTION_CHECK_INTERVAL);
    engine.increaseTime(PendingMessageSubscriptionCheckScheduler.SUBSCRIPTION_CHECK_INTERVAL);
    engine.resumeProcessing(2);

    // await for all 10 messages to be correlated
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(eventSubProcessStartId)
                .limit(10)
                .toList())
        .hasSize(10);

    // complete the task to complete the process instance, for easy record stream limiting
    engine.userTask().ofInstance(processInstanceKey).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId(eventSubProcessStartId)
                .count())
        .describedAs("Expected to correlate 10 messages exactly")
        .isEqualTo(10);
  }

  private int getPartitionId(final String correlationKey) {
    final List<Integer> partitionIds = engine.getPartitionIds();
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(correlationKey), partitionIds.size());
  }
}
