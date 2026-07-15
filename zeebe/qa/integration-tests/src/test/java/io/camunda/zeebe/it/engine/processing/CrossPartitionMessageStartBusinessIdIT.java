/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.grpc.Status;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real-broker (multi-partition) validation of the cross-partition message-start Business ID
 * handshake. The exhaustive behavioural coverage of the handshake, dedup, lock release and retry
 * lives in the engine's multi-partition harness ({@code
 * MessageStartProcessInstanceCrossPartitionHandshakeTest}), which stubs the inter-partition
 * transport. This test complements it by exercising the same flow over a genuine broker with three
 * real partitions and RAFT-backed inter-partition command transport, closing the gap that the
 * cross-partition Business ID path was previously only covered with stubbed inter-partition
 * transport.
 *
 * <p>The {@code correlationKey} and {@code businessId} are chosen so their subscription hashes
 * route to different partitions (P_K != P_B); a precondition check fails loudly if a future hash
 * change degenerates the scenario into a same-partition path.
 */
@ZeebeIntegration
final class CrossPartitionMessageStartBusinessIdIT {

  private static final int PARTITION_COUNT = 3;

  // Strings whose subscription-partition hashes are stable and known: hash("ck-1") and
  // hash("biz-1")
  // route to different partitions under PARTITION_COUNT=3, so the cross-partition arm is exercised.
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";

  private static final String PROCESS_ID = "wf-cross-partition-bizid";
  private static final String MESSAGE_NAME = "start-msg-cross-partition-bizid";
  private static final String JOB_TYPE = "cross-partition-bizid-task";

  // A process with a none-start event and a message-start event sharing the same bpmnProcessId, so
  // the message-start handshake and CreateProcessInstance hit the same Business ID uniqueness
  // index.
  private static final BpmnModelInstance DUAL_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("noneStart")
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .endEvent()
          .moveToProcess(PROCESS_ID)
          .startEvent("msgStart")
          .message(MESSAGE_NAME)
          .connectTo("task")
          .done();

  @TestZeebe(partitionCount = PARTITION_COUNT)
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnauthenticatedAccess()
          .withUnifiedConfig(
              c -> {
                c.getProcessInstanceCreation().setBusinessIdUniquenessEnabled(true);
                c.getCluster().setPartitionCount(PARTITION_COUNT);
              });

  @AutoClose private CamundaClient client;

  @BeforeEach
  void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();

    // Precondition: the two keys must hash to different partitions, otherwise the cross-partition
    // arm is not exercised. Fail loudly if a hash change silently degenerates the scenario.
    assertThat(partitionFor(CORRELATION_KEY))
        .as(
            "CORRELATION_KEY (%s) and BUSINESS_ID (%s) must hash to different partitions",
            CORRELATION_KEY, BUSINESS_ID)
        .isNotEqualTo(partitionFor(BUSINESS_ID));
  }

  @Test
  void shouldStartAndEnforceUniquenessViaCrossPartitionMessageStartWithBusinessId() {
    // given a deployed message-start process whose start-event subscription exists on every
    // partition
    client
        .newDeployResourceCommand()
        .addProcessModel(DUAL_START_PROCESS, "process.bpmn")
        .send()
        .join();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .limit(PARTITION_COUNT)
        .asList();

    // when a message is published that lands on P_K (hash(correlationKey)) but whose Business ID
    // hashes to P_B (hash(businessId))
    client
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey(CORRELATION_KEY)
        .businessId(BUSINESS_ID)
        .send()
        .join();

    // then a process instance is started on P_B carrying the Business ID
    final var activating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(activating.getValue().getBusinessId()).isEqualTo(BUSINESS_ID);
    assertThat(Protocol.decodePartitionId(activating.getValue().getProcessInstanceKey()))
        .as("the new instance lives on P_B = hash(businessId), not on P_K = hash(correlationKey)")
        .isEqualTo(partitionFor(BUSINESS_ID));

    // and once the holder's job exists (the uniqueness index on P_B is populated)
    RecordingExporter.jobRecords(JobIntent.CREATED).withType(JOB_TYPE).getFirst();

    // a CreateProcessInstance with the same Business ID is rejected across partitions
    assertThatThrownBy(
            () ->
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId(PROCESS_ID)
                    .latestVersion()
                    .businessId(BUSINESS_ID)
                    .send()
                    .join())
        .isInstanceOfSatisfying(
            ClientStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(Status.Code.ALREADY_EXISTS));
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
