/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue.MessageStartLockReleaseHolderValue;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class StorageOrdinalCrossPartitionMessageStartTest {

  private static final int PARTITION_COUNT = 3;
  private static final int FIXED_ORDINAL = 1234;
  private static final String PROCESS_ID = "ordinal-cross-partition-start";
  private static final String MESSAGE_NAME = "ordinal-cross-partition-message";
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              c ->
                  c.setBusinessIdUniquenessEnabled(true)
                      .setArchiverlessEnabled(true)
                      .setFixedStorageOrdinal(FIXED_ORDINAL)
                      .setMessageStartLockReleasePollInterval(POLL_INTERVAL));

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .as("constants must hash to different partitions to exercise the cross-partition arm")
        .isNotEqualTo(partitionFor(BUSINESS_ID));
  }

  @Test
  public void shouldAssignConfiguredOrdinalToCrossPartitionMessageStartRecords() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent("message-start")
                .message(MESSAGE_NAME)
                .endEvent()
                .done())
        .deploy();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .limit(PARTITION_COUNT)
        .asList();

    // when: the publish lands on P_K and the instance is created on P_B
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // then: the instance on P_B carries the ordinal
    final var creation =
        RecordingExporter.processInstanceCreationRecords()
            .withBpmnProcessId(PROCESS_ID)
            .getFirst()
            .getValue();
    assertThat(creation.getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // and: both STARTED events transport it
    assertThat(
            RecordingExporter.messageStartProcessInstanceRequestRecords(
                    MessageStartProcessInstanceRequestIntent.STARTED)
                .filter(
                    r -> r.getValue().getProcessInstanceKey() == creation.getProcessInstanceKey())
                .limit(2))
        .extracting(r -> r.getValue().getStorageOrdinal())
        .containsOnly(FIXED_ORDINAL);

    // and: the ask itself belongs to no instance yet, on the wire and as acknowledged on both
    // partitions
    assertThat(
            RecordingExporter.messageStartProcessInstanceRequestRecords(
                    MessageStartProcessInstanceRequestIntent.REQUEST)
                .withMessageName(MESSAGE_NAME)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
    assertThat(
            RecordingExporter.messageStartProcessInstanceRequestRecords(
                    MessageStartProcessInstanceRequestIntent.REQUESTED)
                .withMessageName(MESSAGE_NAME)
                .limit(2))
        .hasSize(2)
        .extracting(r -> r.getValue().getStorageOrdinal())
        .containsOnly(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);

    // and: P_K stamps its CORRELATED event from the reply
    final var correlated =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CORRELATED)
            .withBpmnProcessId(PROCESS_ID)
            .getFirst();
    assertThat(correlated.getPartitionId()).isEqualTo(partitionFor(CORRELATION_KEY));
    assertThat(correlated.getValue().getProcessInstanceKey())
        .isEqualTo(creation.getProcessInstanceKey());
    assertThat(correlated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);

    // and: the holder completes right away, so the push path carries the ordinal on its PUSHED
    // event, the RELEASE it sends to P_K and the RELEASED event P_K applies
    final long holderKey = creation.getProcessInstanceKey();
    assertLockReleaseHolderOrdinal(MessageStartCorrelationKeyLockReleaseIntent.PUSHED, holderKey);
    assertLockReleaseHolderOrdinal(MessageStartCorrelationKeyLockReleaseIntent.RELEASE, holderKey);
    assertLockReleaseHolderOrdinal(MessageStartCorrelationKeyLockReleaseIntent.RELEASED, holderKey);
  }

  @Test
  public void shouldAssignConfiguredOrdinalToLockReleaseQueryRecords() {
    // given a holder that stays active, so the reconciliation poll on P_K queries it
    final String processId = "ordinal-cross-partition-poll";
    final String messageName = "ordinal-cross-partition-poll-message";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("message-start")
                .message(messageName)
                .serviceTask("task", t -> t.zeebeJobType("ordinal-hold"))
                .endEvent()
                .done())
        .deploy();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(messageName)
        .limit(PARTITION_COUNT)
        .asList();
    engine
        .message()
        .withName(messageName)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();
    final long holderKey =
        RecordingExporter.processInstanceCreationRecords()
            .withBpmnProcessId(processId)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    // the lock on P_K is written once the STARTED reply is applied there
    RecordingExporter.messageStartProcessInstanceRequestRecords(
            MessageStartProcessInstanceRequestIntent.STARTED)
        .filter(r -> r.getValue().getProcessInstanceKey() == holderKey)
        .limit(2)
        .asList();

    // when the reconciliation poll fires
    engine.increaseTime(POLL_INTERVAL.multipliedBy(2));

    // then the QUERY sent by P_K and the QUERIED acknowledged on P_B carry the holder's ordinal
    assertLockReleaseHolderOrdinal(MessageStartCorrelationKeyLockReleaseIntent.QUERY, holderKey);
    assertLockReleaseHolderOrdinal(MessageStartCorrelationKeyLockReleaseIntent.QUERIED, holderKey);
  }

  private static void assertLockReleaseHolderOrdinal(
      final MessageStartCorrelationKeyLockReleaseIntent intent, final long holderKey) {
    final var record =
        RecordingExporter.messageStartCorrelationKeyLockReleaseRecords(intent)
            .filter(
                r ->
                    r.getValue().getHolders().stream()
                        .anyMatch(h -> h.getProcessInstanceKey() == holderKey))
            .getFirst();
    assertThat(record.getValue().getHolders())
        .as("%s carries the holder with the configured ordinal", intent)
        .filteredOn(h -> h.getProcessInstanceKey() == holderKey)
        .singleElement()
        .extracting(MessageStartLockReleaseHolderValue::getStorageOrdinal)
        .isEqualTo(FIXED_ORDINAL);
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
