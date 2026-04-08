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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for priority-based job activation. Jobs can have a priority (integer, higher = more
 * important). When workers activate jobs with {@code usePriority=true}, jobs are returned in
 * priority order (highest first). Jobs with the same priority are returned in FIFO order.
 */
public final class JobPriorityActivationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String taskType;

  @Before
  public void setup() {
    taskType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldActivateJobsInPriorityOrderWhenUsePriorityIsTrue() {
    // given - deploy processes with different priorities and create jobs
    final String lowPriorityProcess = "low-priority";
    final String highPriorityProcess = "high-priority";

    final BpmnModelInstance lowPriorityModel =
        Bpmn.createExecutableProcess(lowPriorityProcess)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(taskType).zeebeJobPriority("10"))
            .endEvent()
            .done();

    final BpmnModelInstance highPriorityModel =
        Bpmn.createExecutableProcess(highPriorityProcess)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(taskType).zeebeJobPriority("90"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource("low.bpmn", lowPriorityModel).deploy();
    ENGINE.deployment().withXmlResource("high.bpmn", highPriorityModel).deploy();

    // Create low priority job first
    final long lowPriorityInstance =
        ENGINE.processInstance().ofBpmnProcessId(lowPriorityProcess).create();
    // Create high priority job second
    final long highPriorityInstance =
        ENGINE.processInstance().ofBpmnProcessId(highPriorityProcess).create();

    // Wait for both jobs to be created
    jobRecords(JobIntent.CREATED)
        .withType(taskType)
        .filter(r -> r.getValue().getProcessInstanceKey() == lowPriorityInstance)
        .getFirst();
    jobRecords(JobIntent.CREATED)
        .withType(taskType)
        .filter(r -> r.getValue().getProcessInstanceKey() == highPriorityInstance)
        .getFirst();

    // when - activate jobs with usePriority=true
    final List<JobRecordValue> jobs =
        ENGINE
            .jobs()
            .withType(taskType)
            .withMaxJobsToActivate(10)
            .withUsePriority(true)
            .activate()
            .getValue()
            .getJobs();

    // then - high priority job should come first (even though it was created second)
    assertThat(jobs).hasSize(2);
    assertThat(jobs.get(0).getPriority()).isEqualTo(90);
    assertThat(jobs.get(0).getProcessInstanceKey()).isEqualTo(highPriorityInstance);
    assertThat(jobs.get(1).getPriority()).isEqualTo(10);
    assertThat(jobs.get(1).getProcessInstanceKey()).isEqualTo(lowPriorityInstance);
  }

  @Test
  public void shouldActivateJobsInFifoOrderWhenUsePriorityIsFalse() {
    // given - deploy processes with different priorities and create jobs
    final String lowPriorityProcess = "low-priority-fifo";
    final String highPriorityProcess = "high-priority-fifo";

    final BpmnModelInstance lowPriorityModel =
        Bpmn.createExecutableProcess(lowPriorityProcess)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(taskType).zeebeJobPriority("10"))
            .endEvent()
            .done();

    final BpmnModelInstance highPriorityModel =
        Bpmn.createExecutableProcess(highPriorityProcess)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(taskType).zeebeJobPriority("90"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource("low-fifo.bpmn", lowPriorityModel).deploy();
    ENGINE.deployment().withXmlResource("high-fifo.bpmn", highPriorityModel).deploy();

    // Create low priority job first
    final long lowPriorityInstance =
        ENGINE.processInstance().ofBpmnProcessId(lowPriorityProcess).create();
    // Create high priority job second
    final long highPriorityInstance =
        ENGINE.processInstance().ofBpmnProcessId(highPriorityProcess).create();

    // Wait for both jobs to be created
    jobRecords(JobIntent.CREATED)
        .withType(taskType)
        .filter(r -> r.getValue().getProcessInstanceKey() == lowPriorityInstance)
        .getFirst();
    jobRecords(JobIntent.CREATED)
        .withType(taskType)
        .filter(r -> r.getValue().getProcessInstanceKey() == highPriorityInstance)
        .getFirst();

    // when - activate jobs with usePriority=false (default FIFO behavior)
    final List<JobRecordValue> jobs =
        ENGINE
            .jobs()
            .withType(taskType)
            .withMaxJobsToActivate(10)
            .withUsePriority(false)
            .activate()
            .getValue()
            .getJobs();

    // then - jobs should be in FIFO order (low priority first because it was created first)
    assertThat(jobs).hasSize(2);
    assertThat(jobs.get(0).getProcessInstanceKey()).isEqualTo(lowPriorityInstance);
    assertThat(jobs.get(1).getProcessInstanceKey()).isEqualTo(highPriorityInstance);
  }

  @Test
  public void shouldActivateJobsWithSamePriorityInFifoOrder() {
    // given - create multiple jobs with the same priority
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(taskType).zeebeJobPriority("50"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(PROCESS_ID + ".bpmn", model).deploy();

    // Create jobs in order
    final long instance1 = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long instance2 = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final long instance3 = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // Wait for all jobs to be created
    jobRecords(JobIntent.CREATED)
        .withType(taskType)
        .filter(r -> r.getValue().getProcessInstanceKey() == instance3)
        .getFirst();

    // when - activate jobs with usePriority=true
    final List<JobRecordValue> jobs =
        ENGINE
            .jobs()
            .withType(taskType)
            .withMaxJobsToActivate(10)
            .withUsePriority(true)
            .activate()
            .getValue()
            .getJobs();

    // then - jobs with same priority should be in FIFO order
    assertThat(jobs).hasSize(3);
    assertThat(jobs.get(0).getProcessInstanceKey()).isEqualTo(instance1);
    assertThat(jobs.get(1).getProcessInstanceKey()).isEqualTo(instance2);
    assertThat(jobs.get(2).getProcessInstanceKey()).isEqualTo(instance3);
  }

  @Test
  public void shouldUseDefaultPriorityZeroWhenNotSpecified() {
    // given - create a job without explicit priority
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(taskType))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(PROCESS_ID + ".bpmn", model).deploy();
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when - activate the job
    final List<JobRecordValue> jobs =
        ENGINE
            .jobs()
            .withType(taskType)
            .withMaxJobsToActivate(1)
            .withUsePriority(true)
            .activate()
            .getValue()
            .getJobs();

    // then - job should have default priority 0
    assertThat(jobs).hasSize(1);
    assertThat(jobs.get(0).getPriority()).isEqualTo(0);
  }

  @Test
  public void shouldEvaluatePriorityExpression() {
    // given - create jobs with priority expression
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(
                "task", t -> t.zeebeJobType(taskType).zeebeJobPriorityExpression("basePriority"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(PROCESS_ID + ".bpmn", model).deploy();

    // Create job with low base priority
    final long lowInstance =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("basePriority", 10))
            .create();

    // Create job with high base priority
    final long highInstance =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("basePriority", 90))
            .create();

    // Wait for jobs to be created
    jobRecords(JobIntent.CREATED)
        .withType(taskType)
        .filter(r -> r.getValue().getProcessInstanceKey() == highInstance)
        .getFirst();

    // when - activate jobs with usePriority=true
    final List<JobRecordValue> jobs =
        ENGINE
            .jobs()
            .withType(taskType)
            .withMaxJobsToActivate(10)
            .withUsePriority(true)
            .activate()
            .getValue()
            .getJobs();

    // then - high priority job should come first
    assertThat(jobs).hasSize(2);
    assertThat(jobs.get(0).getPriority()).isEqualTo(90);
    assertThat(jobs.get(0).getProcessInstanceKey()).isEqualTo(highInstance);
    assertThat(jobs.get(1).getPriority()).isEqualTo(10);
    assertThat(jobs.get(1).getProcessInstanceKey()).isEqualTo(lowInstance);
  }

  @Test
  public void shouldActivateMixedPriorityJobsInCorrectOrder() {
    // given - create jobs with various priorities including default (0)
    final String noP = "no-priority";
    final String lowP = "low-priority";
    final String medP = "med-priority";
    final String highP = "high-priority";

    ENGINE
        .deployment()
        .withXmlResource(
            "no.bpmn",
            Bpmn.createExecutableProcess(noP)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(taskType)) // default priority 0
                .endEvent()
                .done())
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            "low.bpmn",
            Bpmn.createExecutableProcess(lowP)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(taskType).zeebeJobPriority("25"))
                .endEvent()
                .done())
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            "med.bpmn",
            Bpmn.createExecutableProcess(medP)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(taskType).zeebeJobPriority("50"))
                .endEvent()
                .done())
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            "high.bpmn",
            Bpmn.createExecutableProcess(highP)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(taskType).zeebeJobPriority("75"))
                .endEvent()
                .done())
        .deploy();

    // Create jobs in mixed order
    final long noInstance = ENGINE.processInstance().ofBpmnProcessId(noP).create();
    final long medInstance = ENGINE.processInstance().ofBpmnProcessId(medP).create();
    final long lowInstance = ENGINE.processInstance().ofBpmnProcessId(lowP).create();
    final long highInstance = ENGINE.processInstance().ofBpmnProcessId(highP).create();

    // Wait for all jobs
    jobRecords(JobIntent.CREATED)
        .withType(taskType)
        .filter(r -> r.getValue().getProcessInstanceKey() == highInstance)
        .getFirst();

    // when - activate jobs with usePriority=true
    final List<JobRecordValue> jobs =
        ENGINE
            .jobs()
            .withType(taskType)
            .withMaxJobsToActivate(10)
            .withUsePriority(true)
            .activate()
            .getValue()
            .getJobs();

    // then - jobs should be ordered by priority: 75 > 50 > 25 > 0
    assertThat(jobs).hasSize(4);
    assertThat(jobs.get(0).getPriority()).isEqualTo(75);
    assertThat(jobs.get(1).getPriority()).isEqualTo(50);
    assertThat(jobs.get(2).getPriority()).isEqualTo(25);
    assertThat(jobs.get(3).getPriority()).isEqualTo(0);
  }
}
