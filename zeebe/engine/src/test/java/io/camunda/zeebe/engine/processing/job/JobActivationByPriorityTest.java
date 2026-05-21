/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.CREATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class JobActivationByPriorityTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private String jobType;

  @Before
  public void setUp() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldActivateJobsInPriorityDescendingOrder() {
    // given — three jobs of the same type with different priorities
    for (final int priority : new int[] {10, 50, 90}) {
      final String processId = "priority-desc-" + priority;
      ENGINE
          .deployment()
          .withXmlResource(
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .serviceTask(
                      "task",
                      b -> b.zeebeJobType(jobType).zeebeJobPriority(String.valueOf(priority)))
                  .endEvent()
                  .done())
          .deploy();
      ENGINE.processInstance().ofBpmnProcessId(processId).create();
    }
    RecordingExporter.jobRecords(CREATED).withType(jobType).limit(3).toList();

    // when
    final var batch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(3).activate();

    // then — highest priority first
    assertThat(batch.getValue().getJobs())
        .extracting(JobRecordValue::getPriority)
        .containsExactly(90, 50, 10);
  }

  @Test
  public void shouldActivateJobsInJobKeyAscOrderWithinSamePriority() {
    // given — three jobs with the same priority
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("same-priority")
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType).zeebeJobPriority("50"))
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId("same-priority").create();
    ENGINE.processInstance().ofBpmnProcessId("same-priority").create();
    ENGINE.processInstance().ofBpmnProcessId("same-priority").create();
    RecordingExporter.jobRecords(CREATED).withType(jobType).limit(3).toList();

    // when
    final var batch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(3).activate();

    // then — FIFO within the same priority band (jobKey ASC)
    assertThat(batch.getValue().getJobKeys()).isSorted();
  }

  @Test
  public void shouldActivateDefaultPriorityJobsInJobKeyOrder() {
    // given — three jobs with no explicit priority (default = 0)
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("default-priority")
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId("default-priority").create();
    ENGINE.processInstance().ofBpmnProcessId("default-priority").create();
    ENGINE.processInstance().ofBpmnProcessId("default-priority").create();
    RecordingExporter.jobRecords(CREATED).withType(jobType).limit(3).toList();

    // when
    final var batch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(3).activate();

    // then — FIFO (jobKey ASC = creation order)
    assertThat(batch.getValue().getJobKeys()).isSorted();
    assertThat(batch.getValue().getJobs()).extracting(JobRecordValue::getPriority).containsOnly(0);
  }

  @Test
  public void shouldActivateNegativePriorityJobsAfterZeroPriorityJobs() {
    // given — jobs at priority 5, 0, and -5
    // The CF encodes invertedPriority = Integer.MAX_VALUE - priority.
    // For priority=-1, invertedPriority overflows to Integer.MIN_VALUE (bytes 0x80...).
    // RocksDB unsigned byte order: 0x80... > 0x7F..., so negatives sort correctly after zero.
    for (final int priority : new int[] {-5, 0, 5}) {
      final String processId = "negative-" + priority;
      ENGINE
          .deployment()
          .withXmlResource(
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .serviceTask(
                      "task",
                      b -> b.zeebeJobType(jobType).zeebeJobPriority(String.valueOf(priority)))
                  .endEvent()
                  .done())
          .deploy();
      ENGINE.processInstance().ofBpmnProcessId(processId).create();
    }
    RecordingExporter.jobRecords(CREATED).withType(jobType).limit(3).toList();

    // when
    final var batch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(3).activate();

    // then — 5, then 0, then -5
    assertThat(batch.getValue().getJobs())
        .extracting(JobRecordValue::getPriority)
        .containsExactly(5, 0, -5);
  }

  @Test
  public void shouldNotActivateJobsOfDifferentTenant() {
    // given — tenant A has priority=1 jobs, tenant B has priority=90 jobs (same job type)
    final String tenantA = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    final String tenantB = "tenant-b";

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-tenant-a")
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType).zeebeJobPriority("1"))
                .endEvent()
                .done())
        .withTenantId(tenantA)
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId("process-tenant-a").withTenantId(tenantA).create();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-tenant-b")
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType).zeebeJobPriority("90"))
                .endEvent()
                .done())
        .withTenantId(tenantB)
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId("process-tenant-b").withTenantId(tenantB).create();

    RecordingExporter.jobRecords(CREATED).withType(jobType).limit(2).toList();

    // when — activate for tenant A only
    final var batch = ENGINE.jobs().withType(jobType).withTenantId(tenantA).activate();

    // then — only tenant A's priority=1 job is returned; tenant B's priority=90 job is not
    assertThat(batch.getValue().getJobs()).hasSize(1);
    assertThat(batch.getValue().getJobs().get(0).getPriority()).isEqualTo(1);
    assertThat(batch.getValue().getJobs().get(0).getTenantId()).isEqualTo(tenantA);
  }
}
