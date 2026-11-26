/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * Message start events on a suspended definition across partitions: the correlation key of a
 * message decides the partition that correlates it, so one key per partition covers every local
 * start. The cross-partition start (business id on another partition) has its own test.
 */
public final class MessageStartOnSuspendedDefinitionMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;
  private static final Duration TTL = Duration.ofMinutes(10);

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldStartNoInstanceWhileSuspendedAndStartInstancesAfterResumeOnAllPartitions() {
    // given - a message-start process on every partition, suspended on all of them, and one
    // correlation key per partition
    final var processId = helper.getBpmnProcessId();
    final var messageName = helper.getMessageName();
    final long processDefinitionKey = deployMessageStartProcess(processId, messageName);
    final var keysPerPartition = correlationKeyPerPartition();
    assertDistributionFinished(
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend());

    // when - every key is published while the definition is suspended
    keysPerPartition.forEach(key -> publish(messageName, key, null));
    final var resumed =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .resume();
    assertDistributionFinished(resumed);

    // then - nothing was correlated or started while it was suspended
    final var beforeResume =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == ProcessDefinitionSuspensionIntent.RESUMED)
            .asList();
    assertThat(beforeResume)
        .extracting(Record::getIntent)
        .doesNotContain(
            MessageStartEventSubscriptionIntent.CORRELATED,
            ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when - the same keys are published after the resume
    keysPerPartition.forEach(key -> publish(messageName, key, null));

    // then - none of the keys was left locked, so each one starts an instance
    final int expectedInstances = keysPerPartition.size();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .limit(expectedInstances)
                .count())
        .isEqualTo(expectedInstances);
  }

  @Test
  public void shouldAnswerNotCorrelatedWhenCrossPartitionStartTargetIsSuspended() {
    // given - a suspended definition and a correlate whose business id belongs to another partition
    final var processId = helper.getBpmnProcessId();
    final var messageName = helper.getMessageName();
    final long processDefinitionKey = deployMessageStartProcess(processId, messageName);
    final var correlationKey = correlationKeyPerPartition().getFirst();
    final var businessId = businessIdOnOtherPartitionThan(correlationKey);
    assertDistributionFinished(
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend());

    // when
    final var notCorrelated =
        engine
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .withBusinessId(businessId)
            .expectNotCorrelated()
            .correlate();

    // then - the deferred NO_SUBSCRIPTION reply ends the correlate instead of leaving it waiting
    assertThat(notCorrelated.getIntent()).isEqualTo(MessageCorrelationIntent.NOT_CORRELATED);
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == MessageCorrelationIntent.NOT_CORRELATED)
                .asList())
        .extracting(Record::getIntent)
        .doesNotContain(
            MessageStartEventSubscriptionIntent.CORRELATED,
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  @Test
  public void shouldNotHoldBusinessIdWhileSuspendedForStartOnTheSamePartition() {
    // given - a suspended definition and a business id on the same partition as the correlation key
    final var processId = helper.getBpmnProcessId();
    final var messageName = helper.getMessageName();
    final long processDefinitionKey = deployMessageStartProcess(processId, messageName);
    final var correlationKey = correlationKeyPerPartition().getFirst();
    final var businessId = businessIdOnSamePartitionAs(correlationKey);
    assertDistributionFinished(
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .suspend());

    // when - the message is published while suspended, then the definition is resumed
    publish(messageName, correlationKey, businessId);
    assertDistributionFinished(
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(processDefinitionKey)
            .resume());
    final var beforeResume =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == ProcessDefinitionSuspensionIntent.RESUMED)
            .asList();
    publish(messageName, correlationKey, businessId);

    // then - nothing started while suspended, and neither key nor business id stayed held
    assertThat(beforeResume)
        .extracting(Record::getIntent)
        .doesNotContain(
            MessageStartEventSubscriptionIntent.CORRELATED,
            ProcessInstanceIntent.ELEMENT_ACTIVATED);
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(processId)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue()
                .getBusinessId())
        .isEqualTo(businessId);
  }

  private long deployMessageStartProcess(final String processId, final String messageName) {
    final var metadata =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .message(messageName)
                    .serviceTask("task", t -> t.zeebeJobType(helper.getJobType()))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();
    // every partition needs its subscription before the first publish
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(messageName)
        .limit(PARTITION_COUNT)
        .asList();
    return metadata.getProcessDefinitionKey();
  }

  private void publish(
      final String messageName, final String correlationKey, final String businessId) {
    var message =
        engine
            .message()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .withTimeToLive(TTL);
    if (businessId != null) {
      message = message.withBusinessId(businessId);
    }
    message.publish();
  }

  /** One correlation key for each partition, so each partition correlates a local start. */
  private static List<String> correlationKeyPerPartition() {
    final var keys = new ArrayList<String>();
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      int candidate = 0;
      while (partitionFor("ck-" + candidate) != partitionId) {
        candidate++;
      }
      keys.add("ck-" + candidate);
    }
    return keys;
  }

  private static String businessIdOnOtherPartitionThan(final String correlationKey) {
    int candidate = 0;
    while (partitionFor("biz-" + candidate) == partitionFor(correlationKey)) {
      candidate++;
    }
    return "biz-" + candidate;
  }

  private static String businessIdOnSamePartitionAs(final String correlationKey) {
    int candidate = 0;
    while (partitionFor("biz-" + candidate) != partitionFor(correlationKey)) {
      candidate++;
    }
    return "biz-" + candidate;
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }

  private static void assertDistributionFinished(final Record<?> requestEvent) {
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .withPartitionId(1)
                .withRecordKey(requestEvent.getKey())
                .exists())
        .describedAs("the distribution of %s finished", requestEvent.getIntent())
        .isTrue();
  }
}
