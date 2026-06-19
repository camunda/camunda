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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class JobActivationByPriorityTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withInitialClusterVersionAtMax();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private String jobType;

  @Before
  public void setUp() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldActivateJobsInJobKeyAscOrderWithinSamePriority() {
    // given
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
    awaitJobsCreated(3);

    // when
    final var batch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(3).activate();

    // then
    assertThat(batch.getValue().getJobKeys()).isSorted();
  }

  @Test
  public void shouldActivateJobsInPriorityDescendingOrder() {
    // given
    final var priorities =
        new int[] {
          Integer.MIN_VALUE,
          -1,
          0,
          1,
          Integer.MAX_VALUE - 1,
          Integer.MAX_VALUE,
          Integer.MIN_VALUE + 1
        };
    for (final int priority : priorities) {
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

    awaitJobsCreated(priorities.length);

    // when
    final var batch =
        ENGINE.jobs().withType(jobType).withMaxJobsToActivate(priorities.length).activate();

    // then
    assertThat(batch.getValue().getJobs())
        .extracting(JobRecordValue::getPriority)
        .containsExactly(
            Integer.MAX_VALUE,
            Integer.MAX_VALUE - 1,
            1,
            0,
            -1,
            Integer.MIN_VALUE + 1,
            Integer.MIN_VALUE);
  }

  @Test
  public void shouldActivateDefaultPriorityJobsInJobKeyOrder() {
    // given
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
    awaitJobsCreated(3);

    // when
    final var batch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(3).activate();

    // then
    assertThat(batch.getValue().getJobKeys()).isSorted();
    assertThat(batch.getValue().getJobs()).extracting(JobRecordValue::getPriority).containsOnly(0);
  }

  @Test
  public void shouldNotActivateJobsOfDifferentTenant() {
    // given
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

    awaitJobsCreated(2);

    // when
    final var batch = ENGINE.jobs().withType(jobType).withTenantId(tenantA).activate();

    // then
    assertThat(batch.getValue().getJobs()).hasSize(1);
    assertThat(batch.getValue().getJobs().getFirst().getPriority()).isEqualTo(1);
    assertThat(batch.getValue().getJobs().getFirst().getTenantId()).isEqualTo(tenantA);
  }

  @Test
  public void shouldServeLowerPriorityJobsInNextBatchWhenHigherPriorityFillsBatch() {
    // given two priority>0 jobs and one priority=0 job of the same type
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("high-priority")
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType).zeebeJobPriority("50"))
                .endEvent()
                .done())
        .deploy();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("zero-priority")
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    ENGINE.processInstance().ofBpmnProcessId("high-priority").create();
    ENGINE.processInstance().ofBpmnProcessId("high-priority").create();
    ENGINE.processInstance().ofBpmnProcessId("zero-priority").create();
    awaitJobsCreated(3);

    // when activating with maxJobsToActivate equal to the number of priority>0 jobs
    final var firstBatch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(2).activate();

    // then only the two priority>0 jobs are returned; the phase short-circuit must not skip the
    // remaining lower-priority job permanently
    assertThat(firstBatch.getValue().getJobs())
        .extracting(JobRecordValue::getPriority)
        .containsOnly(50);

    // and the priority=0 job is still activatable on the next call
    final var secondBatch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(10).activate();
    assertThat(secondBatch.getValue().getJobs())
        .extracting(JobRecordValue::getPriority)
        .containsExactly(0);
  }

  @Test
  public void shouldReactivateAtOriginalPriorityAfterRetryBackoffElapses() {
    // given a deployed priority=50 job that has been activated
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("backoff-priority")
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType(jobType).zeebeJobPriority("50"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("backoff-priority").create();
    awaitJobsCreated(1);
    final var firstBatch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(1).activate();
    final long jobKey = firstBatch.getValue().getJobKeys().getFirst();
    assertThat(firstBatch.getValue().getJobs().getFirst().getPriority()).isEqualTo(50);

    // when the job fails with a retry backoff and the backoff period elapses
    final Duration backOff = Duration.ofDays(1);
    ENGINE
        .job()
        .withKey(jobKey)
        .ofInstance(processInstanceKey)
        .withRetries(3)
        .withBackOff(backOff)
        .fail();
    ENGINE.increaseTime(
        backOff.plus(Duration.ofMillis(JobBackoffCheckScheduler.BACKOFF_RESOLUTION)));
    RecordingExporter.jobRecords(JobIntent.RECURRED_AFTER_BACKOFF)
        .withType(jobType)
        .withRecordKey(jobKey)
        .await();

    // then the job is activatable again at its original priority, exactly once
    final var secondBatch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(10).activate();
    assertThat(secondBatch.getValue().getJobKeys()).containsExactly(jobKey);
    assertThat(secondBatch.getValue().getJobs().getFirst().getPriority()).isEqualTo(50);

    // and there is no stale column-family entry: a further activation returns nothing
    final var thirdBatch = ENGINE.jobs().withType(jobType).withMaxJobsToActivate(10).activate();
    assertThat(thirdBatch.getValue().getJobs()).isEmpty();
  }

  private void awaitJobsCreated(final int count) {
    RecordingExporter.jobRecords(CREATED).withType(jobType).limit(count).toList();
  }
}
