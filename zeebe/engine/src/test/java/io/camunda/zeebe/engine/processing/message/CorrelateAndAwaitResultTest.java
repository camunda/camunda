/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.stubbing.Answer;

/** SPIKE tests for variant B1: correlate a message and await the instance result. */
public final class CorrelateAndAwaitResultTest {

  private static final int PARTITION_COUNT = 3;
  private static final String QUICK_MSG = "quick-msg";
  private static final String SLOW_MSG = "slow-msg";
  // hash("ck-1") -> partition 1, hash("biz-1") -> partition 3
  private static final String CORRELATION_KEY = "ck-1";
  private static final String OTHER_PARTITION_BUSINESS_ID = "biz-1";

  private static final BpmnModelInstance QUICK =
      Bpmn.createExecutableProcess("quick")
          .startEvent()
          .message(QUICK_MSG)
          .endEvent()
          .done();

  private static final BpmnModelInstance SLOW =
      Bpmn.createExecutableProcess("slow")
          .startEvent()
          .message(SLOW_MSG)
          .serviceTask("task", t -> t.zeebeJobType("work"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  private CommandResponseWriter writer;
  private volatile ProcessInstanceResultRecord response;

  @Before
  public void setUp() {
    writer = engine.getCommandResponseWriter();
    doAnswer(
            (Answer<CommandResponseWriter>)
                invocation -> {
                  if (invocation.getArguments()[0] instanceof final ProcessInstanceResultRecord r) {
                    response = r;
                  }
                  return writer;
                })
        .when(writer)
        .valueWriter(any());
    engine.deployment().withXmlResource(QUICK).withXmlResource(SLOW).deploy();
    for (final var name : List.of(QUICK_MSG, SLOW_MSG)) {
      RecordingExporter.messageStartEventSubscriptionRecords(
              MessageStartEventSubscriptionIntent.CREATED)
          .withMessageName(name)
          .limit(PARTITION_COUNT)
          .await();
    }
  }

  // Q2 + Q3: a process that completes inside the same batch cascade still gets its waiter
  @Test
  public void shouldAnswerWithKeyAndRequestedVariablesWhenProcessCompletesImmediately() {
    // when
    engine
        .messageCorrelation()
        .withName(QUICK_MSG)
        .withCorrelationKey(CORRELATION_KEY)
        .withVariables(Map.of("a", 1, "b", 2))
        .correlateAndAwaitResult(11L, 7, List.of("a"));

    // then exactly one response, and it is the result, not CORRELATED
    verify(writer, timeout(5000).times(1)).tryWriteResponse(7, 11L);
    verify(writer, timeout(5000).times(1)).intent(ProcessInstanceResultIntent.COMPLETED);
    final var completed =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(response.getProcessInstanceKey()).isEqualTo(completed.getKey());
    assertThat(response.getVariables()).containsExactly(Map.entry("a", 1));
  }

  @Test
  public void shouldReturnAllVariablesWhenFetchVariablesIsEmpty() {
    // when
    engine
        .messageCorrelation()
        .withName(QUICK_MSG)
        .withCorrelationKey(CORRELATION_KEY)
        .withVariables(Map.of("a", 1, "b", 2))
        .correlateAndAwaitResult(12L, 8, List.of());

    // then
    verify(writer, timeout(5000).times(1)).tryWriteResponse(8, 12L);
    assertThat(response.getVariables()).containsOnly(Map.entry("a", 1), Map.entry("b", 2));
  }

  @Test
  public void shouldAnswerOnlyAfterTheInstanceCompletes() {
    // given
    engine
        .messageCorrelation()
        .withName(SLOW_MSG)
        .withCorrelationKey(CORRELATION_KEY)
        .correlateAndAwaitResult(13L, 9, List.of());
    final long key =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();
    verify(writer, after(500).never()).tryWriteResponse(9, 13L);

    // when
    engine.job().ofInstance(key).withType("work").complete();

    // then
    verify(writer, timeout(5000).times(1)).tryWriteResponse(9, 13L);
    assertThat(response.getProcessInstanceKey()).isEqualTo(key);
  }

  // Q4: incident
  @Test
  public void shouldNotAnswerWhileInstanceIsInIncident() {
    // given
    engine
        .messageCorrelation()
        .withName(SLOW_MSG)
        .withCorrelationKey(CORRELATION_KEY)
        .correlateAndAwaitResult(14L, 10, List.of());
    final long key =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    // when
    engine.job().ofInstance(key).withType("work").withRetries(0).fail();
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(key)
        .await();

    // then
    verify(writer, after(1000).never()).tryWriteResponse(10, 14L);
  }

  // Q4: termination
  @Test
  public void shouldNotAnswerWhenInstanceIsTerminated() {
    // given
    engine
        .messageCorrelation()
        .withName(SLOW_MSG)
        .withCorrelationKey(CORRELATION_KEY)
        .correlateAndAwaitResult(15L, 11, List.of());
    final long key =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    // when
    engine.processInstance().withInstanceKey(key).cancel();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(key)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    verify(writer, after(1000).never()).tryWriteResponse(11, 15L);
  }

  // Q5: businessId that hashes to another partition
  @Test
  public void shouldRejectWhenBusinessIdHashesToOtherPartition() {
    assertThat(partitionFor(OTHER_PARTITION_BUSINESS_ID)).isNotEqualTo(partitionFor(CORRELATION_KEY));

    // when
    engine
        .messageCorrelation()
        .withName(QUICK_MSG)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(OTHER_PARTITION_BUSINESS_ID)
        .correlateAndAwaitResult(16L, 12, List.of());

    // then
    verify(writer, timeout(5000).times(1)).tryWriteResponse(12, 16L);
    verify(writer, timeout(5000)).rejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withElementType(BpmnElementType.PROCESS)
                .limit(0))
        .isEmpty();
  }

  // Q5: businessId that hashes to the same partition keeps working
  @Test
  public void shouldAwaitResultWhenBusinessIdHashesToSamePartition() {
    // given
    String businessId = "same-0";
    for (int i = 0; partitionFor(businessId) != partitionFor(CORRELATION_KEY); i++) {
      businessId = "same-" + i;
    }

    // when
    engine
        .messageCorrelation()
        .withName(QUICK_MSG)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(businessId)
        .correlateAndAwaitResult(17L, 13, List.of());

    // then
    verify(writer, timeout(5000).times(1)).tryWriteResponse(13, 17L);
    assertThat(response.getBusinessId()).isEqualTo(businessId);
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
