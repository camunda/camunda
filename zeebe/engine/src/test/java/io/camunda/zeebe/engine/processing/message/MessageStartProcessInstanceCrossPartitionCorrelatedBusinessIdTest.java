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
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Cross-partition counterpart of {@link MessageStartEventCorrelatedBusinessIdTest}: when a
 * message-start publish lands on {@code P_K} but its {@code businessId} hashes to a different
 * partition {@code P_B}, the start is delegated to {@code P_B} and the {@code
 * MessageStartEventSubscription:CORRELATED} event is written back on {@code P_K} by {@link
 * io.camunda.zeebe.engine.processing.message.MessageStartProcessInstanceRequestStartProcessor}.
 * This pins that the delegated reply still carries the published {@code businessId} onto that
 * event.
 *
 * <p>The constants are chosen so {@code hash(correlationKey) != hash(businessId)}; an
 * {@code @Before} precondition fails loudly if a future hash change degenerates the scenario into a
 * single-partition path.
 */
public final class MessageStartProcessInstanceCrossPartitionCorrelatedBusinessIdTest {

  private static final int PARTITION_COUNT = 3;

  // hash("ck-1") -> P_K=1 and hash("biz-1") -> P_B=3 under PARTITION_COUNT=3, re-asserted in
  // @Before.
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";

  private static final String PROCESS_ID = "wf-cross";
  private static final String MESSAGE_NAME = "start-msg-cross";

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .as("CORRELATION_KEY and BUSINESS_ID must hash to different partitions")
        .isNotEqualTo(partitionFor(BUSINESS_ID));
  }

  @Test
  public void shouldRecordBusinessIdOnCrossPartitionStartCorrelation() {
    // given
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .limit(PARTITION_COUNT)
        .asList();

    // when a message-start publish lands on P_K but its businessId hashes to P_B
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // then the CORRELATED event written back on P_K by the reply processor carries the businessId
    final var correlated =
        RecordingExporter.messageStartEventSubscriptionRecords(
                MessageStartEventSubscriptionIntent.CORRELATED)
            .withMessageName(MESSAGE_NAME)
            .getFirst();
    assertThat(correlated.getValue().getBusinessId()).isEqualTo(BUSINESS_ID);
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
