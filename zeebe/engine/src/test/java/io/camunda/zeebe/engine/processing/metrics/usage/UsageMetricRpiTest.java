/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics.usage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests that the RPI (Root Process Instance) usage metric is tracked for all process start types:
 * API, message start event, timer start event, signal start event, and conditional start event.
 */
public class UsageMetricRpiTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldTrackRpiMetricWhenProcessStartedViaApi() {
    // given
    final var process = Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.processInstance().ofBpmnProcessId("process").create();

    // then - the process completes, but the RPI count must still be 1
    engine.awaitProcessingOf(
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filterRootScope()
            .getFirst());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 1L);
  }

  @Test
  public void shouldTrackRpiMetricWhenProcessStartedViaPublishMessageStartEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .message("startMessage")
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.message().withName("startMessage").withCorrelationKey("key-1").publish();

    // then - the process completes, but the RPI count must still be 1
    engine.awaitProcessingOf(
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filterRootScope()
            .getFirst());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 1L);
  }

  @Test
  public void shouldTrackRpiMetricWhenProcessStartedViaCorrelateMessageStartEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .message("startMessage")
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.messageCorrelation().withName("startMessage").withCorrelationKey("key-1").correlate();

    // then - the process completes, but the RPI count must still be 1
    engine.awaitProcessingOf(
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filterRootScope()
            .getFirst());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 1L);
  }

  @Test
  public void shouldTrackRpiMetricWhenProcessStartedViaTimerStartEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .timerWithCycle("R1/PT1S")
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.increaseTime(Duration.ofSeconds(2));

    // then - the process completes, but the RPI count must still be 1
    engine.awaitProcessingOf(
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filterRootScope()
            .getFirst());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 1L);
  }

  @Test
  public void shouldTrackRpiMetricWhenProcessStartedViaSignalStartEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .signal("startSignal")
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.signal().withSignalName("startSignal").broadcast();

    // then - the process completes, but the RPI count must still be 1
    engine.awaitProcessingOf(
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filterRootScope()
            .getFirst());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 1L);
  }

  @Test
  public void shouldTrackRpiMetricWhenProcessStartedViaConditionalStartEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .condition(c -> c.condition("=x > y"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // when
    engine.conditionalEvaluation().withVariables(Map.of("x", 1000, "y", 100)).evaluate();

    // then - the process completes, but the RPI count must still be 1
    engine.awaitProcessingOf(
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filterRootScope()
            .getFirst());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 1L);
  }

  @Test
  public void shouldNotTrackRpiMetricWhenRunningProcessReceivesMessageEvent() {
    // given - a process that starts and then waits on an intermediate message catch event
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent("catch")
            .message(m -> m.name("waitMessage").zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // start the process so the baseline RPI count is 1
    engine.processInstance().ofBpmnProcessId("process").withVariable("key", "key-1").create();

    // wait until the instance is waiting at the catch event
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("catch")
        .getFirst();

    // when - the already-running instance receives the message (not a new root process instance)
    engine.message().withName("waitMessage").withCorrelationKey("key-1").publish();

    // then - the process completes, but the RPI count must still be 1
    engine.awaitProcessingOf(
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filterRootScope()
            .getFirst());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 1L);
  }

  @Test
  public void shouldTrackRpiMetricWhenProcessStartedViaBufferedMessageCorrelation() {
    // given - a process with a message start event and a service task to keep the instance alive
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .message("startMessage")
            .serviceTask("task", t -> t.zeebeJobType("task"))
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process).deploy();

    // start the first process instance via message publication
    engine.message().withName("startMessage").withCorrelationKey("key-1").publish();
    final var firstJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType("task").getFirst();

    // publish a second message with the same correlation key — it will be buffered because the
    // first instance holds the correlation key lock
    engine.message().withName("startMessage").withCorrelationKey("key-1").publish();

    // when - completing the first instance triggers buffered message correlation
    engine.job().withKey(firstJob.getKey()).complete();

    // then - wait for the second job to be activated and verify two RPIs are counted
    engine.awaitProcessingOf(
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType("task")
            .skipUntil(r -> r.getKey() != firstJob.getKey())
            .getFirst());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 2L);
  }

  @Test
  public void shouldTrackRpiMetricForEachProcessInstanceStartedViaSignal() {
    // given - two processes with the same signal start event
    final var process1 =
        Bpmn.createExecutableProcess("process1")
            .startEvent()
            .signal("sharedSignal")
            .endEvent()
            .done();
    final var process2 =
        Bpmn.createExecutableProcess("process2")
            .startEvent()
            .signal("sharedSignal")
            .endEvent()
            .done();
    engine.deployment().withXmlResource(process1).withXmlResource(process2).deploy();

    // when
    engine.signal().withSignalName("sharedSignal").broadcast();

    // then - both process instances should be counted after they have been completed
    final var activatedRecords =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .filterRootScope()
            .limit(2)
            .asList();
    assertThat(activatedRecords).hasSize(2);
    engine.awaitProcessingOf(activatedRecords.getLast());

    final var bucket = engine.getProcessingState().getUsageMetricState().getActiveBucket();
    assertThat(bucket).isNotNull();
    assertThat(bucket.getTenantRPIMap()).containsEntry(TenantOwned.DEFAULT_TENANT_IDENTIFIER, 2L);
  }
}
