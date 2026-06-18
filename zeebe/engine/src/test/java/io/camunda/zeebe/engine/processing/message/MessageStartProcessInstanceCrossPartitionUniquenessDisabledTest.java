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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Multi-partition pin for the rule that the {@code businessIdUniquenessEnabled} flag gates only the
 * uniqueness <em>rejection</em>, never the cross-partition routing. A message-start whose Business
 * ID hashes to a different partition than its correlation key is delegated to {@code P_B =
 * hash(businessId)} <em>even when the flag is off</em>, so the structural invariant "every root PI
 * carrying a Business ID lives on {@code P_B}" holds regardless of the flag; {@code P_B} just never
 * rejects on uniqueness while the flag is off.
 *
 * <p>The constants are chosen so {@code hash(correlationKey) != hash(businessId)}; an
 * {@code @Before} precondition fails loudly if a future hash change degenerates the scenario into a
 * single-partition path.
 */
public final class MessageStartProcessInstanceCrossPartitionUniquenessDisabledTest {

  private static final int PARTITION_COUNT = 3;

  // hash("ck-1") -> P_K=1 and hash("biz-1") -> P_B=3 under PARTITION_COUNT=3, re-asserted in
  // @Before.
  private static final String CORRELATION_KEY = "ck-1";
  private static final String OTHER_CORRELATION_KEY = "ck-2";
  private static final String BUSINESS_ID = "biz-1";

  private static final String PROCESS_ID = "wf-cross-disabled";
  private static final String MESSAGE_NAME = "start-msg-disabled";

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(false));

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .as("CORRELATION_KEY and BUSINESS_ID must hash to different partitions")
        .isNotEqualTo(partitionFor(BUSINESS_ID));
  }

  @Test
  public void shouldDelegateRemoteBusinessIdToPBEvenWhenUniquenessDisabled() {
    // given
    deployAndAwaitStartEventSubscriptionsOnAllPartitions();

    // when a message-start publish lands on P_K but its businessId hashes to P_B, with the
    // uniqueness feature disabled
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // then the new PI is still created on P_B — routing/placement is independent of the flag, so
    // the "businessId PI lives on hash(businessId)" invariant holds even with uniqueness off
    final var activating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(PROCESS_ID)
            .getFirst();
    assertThat(Protocol.decodePartitionId(activating.getValue().getProcessInstanceKey()))
        .as("the new PI lives on P_B = hash(businessId), not on P_K = hash(correlationKey)")
        .isEqualTo(partitionFor(BUSINESS_ID));
    assertThat(activating.getValue().getBusinessId()).isEqualTo(BUSINESS_ID);
  }

  @Test
  public void shouldNotRejectSecondRemoteBusinessIdStartWhenUniquenessDisabled() {
    // given a first PI already started on P_B for businessId "biz-1"
    deployAndAwaitStartEventSubscriptionsOnAllPartitions();
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // when a second message reuses the same businessId (different correlation key so the
    // correlation-key lock is not the gate)
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(OTHER_CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // then both PIs start on P_B — with uniqueness off, P_B never replies UNIQUENESS_REJECTED, so
    // the duplicate businessId is allowed (the flag gates the rejection, not the routing)
    final var activatings =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(PROCESS_ID)
            .limit(2)
            .asList();
    assertThat(activatings)
        .hasSize(2)
        .allSatisfy(
            r -> {
              assertThat(r.getValue().getBusinessId()).isEqualTo(BUSINESS_ID);
              assertThat(Protocol.decodePartitionId(r.getValue().getProcessInstanceKey()))
                  .isEqualTo(partitionFor(BUSINESS_ID));
            });
    assertThat(activatings.get(0).getValue().getProcessInstanceKey())
        .isNotEqualTo(activatings.get(1).getValue().getProcessInstanceKey());
  }

  private void deployAndAwaitStartEventSubscriptionsOnAllPartitions() {
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .limit(PARTITION_COUNT)
        .asList();
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
