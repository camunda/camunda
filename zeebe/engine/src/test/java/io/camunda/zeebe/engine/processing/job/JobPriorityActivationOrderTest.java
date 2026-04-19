/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobPriorityActivationOrderTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "priority-test";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance processWithPriorityExpression() {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriorityExpression("p"))
        .endEvent()
        .done();
  }

  private void deploy() {
    ENGINE.deployment().withXmlResource(processWithPriorityExpression()).deploy();
  }

  private void createInstanceWithPriority(final long priority) {
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("p", priority).create();
  }

  private void awaitJobsCreated(final int expectedCount) {
    jobRecords(JobIntent.CREATED).withType(JOB_TYPE).limit(expectedCount).toList();
  }

  private List<Long> activateAndGetPriorities(final int maxJobsToActivate) {
    final Record<JobBatchRecordValue> batch =
        ENGINE.jobs().withType(JOB_TYPE).withMaxJobsToActivate(maxJobsToActivate).activate();
    return batch.getValue().getJobs().stream()
        .map(JobRecordValue::getPriority)
        .collect(Collectors.toList());
  }

  @Test
  public void shouldActivateJobsInPriorityDescendingOrder() {
    // given
    deploy();
    createInstanceWithPriority(50L);
    createInstanceWithPriority(90L);
    createInstanceWithPriority(10L);
    awaitJobsCreated(3);

    // when
    final List<Long> activationOrder = activateAndGetPriorities(3);

    // then
    assertThat(activationOrder).containsExactly(90L, 50L, 10L);
  }

  @Test
  public void shouldActivateHigherPriorityBeforeLower() {
    // given
    deploy();
    createInstanceWithPriority(0L);
    createInstanceWithPriority(100L);
    awaitJobsCreated(2);

    // when
    final List<Long> activationOrder = activateAndGetPriorities(2);

    // then
    assertThat(activationOrder).containsExactly(100L, 0L);
  }

  @Test
  public void shouldActivateSamePriorityInJobKeyOrder() {
    // given — three jobs at the same priority; creation order determines activation order (FIFO)
    deploy();
    createInstanceWithPriority(50L);
    createInstanceWithPriority(50L);
    createInstanceWithPriority(50L);
    awaitJobsCreated(3);

    // when
    final Record<JobBatchRecordValue> batch =
        ENGINE.jobs().withType(JOB_TYPE).withMaxJobsToActivate(3).activate();
    final List<Long> activatedJobKeys = batch.getValue().getJobKeys();

    // then — all same priority, activation order is jobKey ASC (creation order)
    assertThat(activatedJobKeys).isSorted();
    assertThat(batch.getValue().getJobs())
        .extracting(JobRecordValue::getPriority)
        .containsExactly(50L, 50L, 50L);
  }

  @Test
  public void shouldRespectLongMaxValueAsHighestPriority() {
    // given — Long.MAX_VALUE is the highest representable priority and must sort first
    deploy();
    createInstanceWithPriority(100L);
    createInstanceWithPriority(Long.MAX_VALUE);
    createInstanceWithPriority(0L);
    awaitJobsCreated(3);

    // when
    final List<Long> activationOrder = activateAndGetPriorities(3);

    // then
    assertThat(activationOrder).containsExactly(Long.MAX_VALUE, 100L, 0L);
  }

  @Test
  public void shouldRespectLongMinValueAsLowestPriority() {
    // given — Long.MIN_VALUE is the lowest representable priority and must sort last
    deploy();
    createInstanceWithPriority(0L);
    createInstanceWithPriority(-100L);
    createInstanceWithPriority(Long.MIN_VALUE);
    awaitJobsCreated(3);

    // when
    final List<Long> activationOrder = activateAndGetPriorities(3);

    // then
    assertThat(activationOrder).containsExactly(0L, -100L, Long.MIN_VALUE);
  }

  @Test
  public void shouldOrderAcrossFullSignedLongRange() {
    // given — mix of extreme positive, mid-positive, zero, mid-negative, extreme negative
    deploy();
    createInstanceWithPriority(-100L);
    createInstanceWithPriority(Long.MAX_VALUE);
    createInstanceWithPriority(0L);
    createInstanceWithPriority(Long.MIN_VALUE);
    createInstanceWithPriority(100L);
    awaitJobsCreated(5);

    // when
    final List<Long> activationOrder = activateAndGetPriorities(5);

    // then
    assertThat(activationOrder).containsExactly(Long.MAX_VALUE, 100L, 0L, -100L, Long.MIN_VALUE);
  }

  @Test
  public void shouldOrderNegativePrioritiesBelowZero() {
    // given — negative priorities must all sort below zero, and among themselves in DESC order
    deploy();
    createInstanceWithPriority(-50L);
    createInstanceWithPriority(0L);
    createInstanceWithPriority(-1L);
    createInstanceWithPriority(-10L);
    awaitJobsCreated(4);

    // when
    final List<Long> activationOrder = activateAndGetPriorities(4);

    // then — 0 is highest; then -1, -10, -50 in descending (less-negative first)
    assertThat(activationOrder).containsExactly(0L, -1L, -10L, -50L);
  }

  @Test
  public void shouldMaintainPriorityOrderAcrossMultipleActivationBatches() {
    // given — two batches of activation; each should return the highest-priority jobs first
    deploy();
    createInstanceWithPriority(10L);
    createInstanceWithPriority(80L);
    createInstanceWithPriority(50L);
    createInstanceWithPriority(90L);
    awaitJobsCreated(4);

    // when — first batch of 2, then second batch of 2
    final List<Long> firstBatch = activateAndGetPriorities(2);
    final List<Long> secondBatch = activateAndGetPriorities(2);

    // then
    assertThat(firstBatch).containsExactly(90L, 80L);
    assertThat(secondBatch).containsExactly(50L, 10L);
  }

  @Test
  public void shouldActivateJobsWithLiteralExtremePriorities() {
    // given — test via the literal BPMN attribute (not an expression) at the boundary values
    final BpmnModelInstance maxProcess =
        Bpmn.createExecutableProcess("process-max")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriority(String.valueOf(Long.MAX_VALUE)))
            .endEvent()
            .done();
    final BpmnModelInstance minProcess =
        Bpmn.createExecutableProcess("process-min")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType(JOB_TYPE).zeebeJobPriority(String.valueOf(Long.MIN_VALUE)))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(maxProcess).deploy();
    ENGINE.deployment().withXmlResource(minProcess).deploy();

    // create in reverse order to ensure the test distinguishes priority from FIFO
    ENGINE.processInstance().ofBpmnProcessId("process-min").create();
    ENGINE.processInstance().ofBpmnProcessId("process-max").create();
    awaitJobsCreated(2);

    // when
    final List<Long> activationOrder = activateAndGetPriorities(2);

    // then — MAX_VALUE first even though it was created second
    assertThat(activationOrder).containsExactly(Long.MAX_VALUE, Long.MIN_VALUE);
  }
}
