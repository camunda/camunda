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
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

/**
 * A cross-partition {@code ProcessMessageSubscription.CORRELATE} targets an instance of the
 * receiving partition, so its banned instance rejection must carry the ordinal of that instance.
 */
public final class StorageOrdinalCrossPartitionRejectionTest {

  private static final int PARTITION_COUNT = 3;
  private static final int PROCESS_INSTANCE_PARTITION = 1;
  private static final int FIXED_ORDINAL = 1234;

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinals(List.of(FIXED_ORDINAL)));

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  /**
   * The subscription partition writes CORRELATE with the ordinal of its subscription, so the value
   * already carries it and the engine entry restore is skipped. This test pins that engine-written
   * path and passes without the restore. The restore itself is pinned by {@link
   * StorageOrdinalRejectionTest#shouldAssignConfiguredOrdinalToBannedInstanceRejectionOfCorrelateWithoutOrdinal}.
   */
  @Test
  public void shouldAssignConfiguredOrdinalToBannedInstanceRejectionOfCrossPartitionCorrelate() {
    // given: an instance on one partition with its message subscription on another
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final int subscriptionPartitionId = getSubscriptionPartitionId(correlationKey);
    final long processInstanceKey = deployAndStart(processId, messageName, correlationKey);
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // and: the instance is banned
    engine.banInstanceInNewTransaction(PROCESS_INSTANCE_PARTITION, processInstanceKey);
    RecordingExporter.errorRecords().withRecordKey(processInstanceKey).await();

    // when: the subscription partition correlates the message to the banned instance
    engine
        .message()
        .onPartition(subscriptionPartitionId)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .publish();

    // then
    final var rejection =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CORRELATE)
            .onlyCommandRejections()
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("banned");
    assertThat(rejection.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private String correlationKeyForPartitionOtherThan(final int partitionId) {
    for (int i = 0; ; i++) {
      final String candidate = "key-" + i;
      if (getSubscriptionPartitionId(candidate) != partitionId) {
        return candidate;
      }
    }
  }

  private int getSubscriptionPartitionId(final String correlationKey) {
    final List<Integer> partitionIds = engine.getPartitionIds();
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(correlationKey), partitionIds.size());
  }

  private long deployAndStart(
      final String processId, final String messageName, final String correlationKey) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e ->
                        e.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    return engine
        .processInstance()
        .ofBpmnProcessId(processId)
        .onPartition(PROCESS_INSTANCE_PARTITION)
        .create();
  }
}
