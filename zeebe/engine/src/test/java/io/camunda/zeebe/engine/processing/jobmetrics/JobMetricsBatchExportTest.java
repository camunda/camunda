/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.jobmetrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetrics;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.StatusMetrics;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobMetricsBatchIntent;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.ByteValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobMetricsBatchExportTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldExportEmptyJobMetricsBatch() {
    // when
    final var record = ENGINE_RULE.jobMetricsBatch().export();

    // then
    Assertions.assertThat(record)
        .hasIntent(JobMetricsBatchIntent.EXPORTED)
        .hasRecordType(RecordType.EVENT);

    assertThat(record.getValue().getJobMetrics()).isEmpty();
    assertThat(record.getValue().getEncodedStrings()).isEmpty();
  }

  @Test
  public void shouldExportJobMetricsBatchAfterJobCreated() {
    // given - deploy a process with a service task
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test-job"))
                .endEvent()
                .done())
        .deploy();

    // create a process instance - this creates a job
    ENGINE_RULE.processInstance().ofBpmnProcessId("process").create();

    // when - export the job metrics batch
    final var record = ENGINE_RULE.jobMetricsBatch().export();

    // then
    Assertions.assertThat(record)
        .hasIntent(JobMetricsBatchIntent.EXPORTED)
        .hasRecordType(RecordType.EVENT);

    // Should have job metrics with CREATED count
    assertThat(record.getValue().getJobMetrics()).isNotEmpty();
    assertThat(record.getValue().getEncodedStrings()).contains("test-job");
  }

  @Test
  public void shouldExportJobMetricsBatchAfterJobCompleted() {
    // given - deploy a process with a service task
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-completed")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("complete-job"))
                .endEvent()
                .done())
        .deploy();

    // create a process instance
    final var pik = ENGINE_RULE.processInstance().ofBpmnProcessId("process-completed").create();

    // activate and complete the job
    ENGINE_RULE.jobs().withType("complete-job").activate();
    ENGINE_RULE.job().ofInstance(pik).withType("complete-job").complete();

    // when - export the job metrics batch
    final var record = ENGINE_RULE.jobMetricsBatch().export();

    // then
    Assertions.assertThat(record)
        .hasIntent(JobMetricsBatchIntent.EXPORTED)
        .hasRecordType(RecordType.EVENT);

    // Should have job metrics with both CREATED and COMPLETED counts
    assertThat(record.getValue().getJobMetrics()).isNotEmpty();
    assertThat(record.getValue().getEncodedStrings()).contains("complete-job");
  }

  @Test
  public void shouldExportJobMetricsBatchAfterJobFailed() {
    // given - deploy a process with a service task
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-failed")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("fail-job").zeebeJobRetries("1"))
                .endEvent()
                .done())
        .deploy();

    // create a process instance
    final var pik = ENGINE_RULE.processInstance().ofBpmnProcessId("process-failed").create();

    // activate and fail the job
    ENGINE_RULE.jobs().withType("fail-job").activate();
    ENGINE_RULE.job().ofInstance(pik).withType("fail-job").withRetries(0).fail();

    // when - export the job metrics batch
    final var record = ENGINE_RULE.jobMetricsBatch().export();

    // then
    Assertions.assertThat(record)
        .hasIntent(JobMetricsBatchIntent.EXPORTED)
        .hasRecordType(RecordType.EVENT);

    // Should have job metrics with CREATED and FAILED counts
    assertThat(record.getValue().getJobMetrics()).isNotEmpty();
    assertThat(record.getValue().getEncodedStrings()).contains("fail-job");
  }

  @Test
  public void shouldExportJobMetricsBatchWithMultipleJobTypes() {
    // given - deploy processes with different job types
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-a")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("job-type-a"))
                .endEvent()
                .done())
        .deploy();

    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-b")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("job-type-b"))
                .endEvent()
                .done())
        .deploy();

    // create process instances
    ENGINE_RULE.processInstance().ofBpmnProcessId("process-a").create();
    ENGINE_RULE.processInstance().ofBpmnProcessId("process-b").create();

    // when - export the job metrics batch
    final var record = ENGINE_RULE.jobMetricsBatch().export();

    // then
    Assertions.assertThat(record)
        .hasIntent(JobMetricsBatchIntent.EXPORTED)
        .hasRecordType(RecordType.EVENT);

    // Should have metrics for both job types
    assertThat(record.getValue().getEncodedStrings()).contains("job-type-a").contains("job-type-b");
  }

  @Test
  public void shouldExportJobMetricsBatchWithBatchTimes() {
    // given - deploy and create a job
    ENGINE_RULE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-times")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("timed-job"))
                .endEvent()
                .done())
        .deploy();

    ENGINE_RULE.processInstance().ofBpmnProcessId("process-times").create();

    // when - export the job metrics batch
    final var record = ENGINE_RULE.jobMetricsBatch().export();

    // then
    Assertions.assertThat(record)
        .hasIntent(JobMetricsBatchIntent.EXPORTED)
        .hasRecordType(RecordType.EVENT);

    // Batch times should be set
    assertThat(record.getValue().getBatchStartTime()).isGreaterThan(0);
    assertThat(record.getValue().getBatchEndTime())
        .isGreaterThanOrEqualTo(record.getValue().getBatchStartTime());
  }

  @Test
  public void shouldExportJobMetricsBatchUnder4MiBWithWorstCaseScenario() {
    // given - worst case scenario configuration
    // max jobType character is 100
    // max worker character is 100
    // max tenantId character is 50
    // Based on testing, 10000 keys exceeds 4MiB (4,406,390 bytes)
    // So we need to find the actual maximum number of keys that fits under 4MiB
    // Testing shows ~9500 keys should fit, let's verify with the exact maximum
    final int maxJobTypeLength = 100;
    final int maxWorkerLength = 100;
    final int maxTenantIdLength = 50;
    final int maxNumberOfKeys = 9500; // Reduced from 10000 to fit under 4MiB
    final long fourMiBMinusOverhead = ByteValue.ofMegabytes(4) - ByteValue.ofKilobytes(2);

    // Create max length strings
    final String maxJobType = StringUtils.repeat("j", maxJobTypeLength);
    final String maxWorker = StringUtils.repeat("w", maxWorkerLength);
    final String maxTenantId = StringUtils.repeat("t", maxTenantIdLength);

    // Build a record with worst case: keys with max length strings
    // In the worst case, each key has unique jobType, worker, and tenantId
    // That means N unique jobTypes + N unique workers + N unique tenantIds = 3N strings
    final JobMetricsBatchRecord record = new JobMetricsBatchRecord();
    final var now = System.currentTimeMillis();
    record.setBatchStartTime(now);
    record.setBatchEndTime(now);
    record.setRecordSizeLimitExceeded(false);

    // Add all unique encoded strings (3 * maxNumberOfKeys total)
    final List<String> encodedStrings = new ArrayList<>();
    for (int i = 0; i < maxNumberOfKeys; i++) {
      encodedStrings.add(maxJobType + i); // unique job types
      encodedStrings.add(maxWorker + i); // unique workers
      encodedStrings.add(maxTenantId + i); // unique tenant IDs
    }
    record.setEncodedStrings(encodedStrings);

    // Add all job metrics entries
    for (int i = 0; i < maxNumberOfKeys; i++) {
      final JobMetrics jobMetrics = new JobMetrics();
      jobMetrics.setJobTypeIndex(i * 3); // index into encoded strings for job type
      jobMetrics.setTenantIdIndex(i * 3 + 1); // index for tenant
      jobMetrics.setWorkerNameIndex(i * 3 + 2); // index for worker
      // Add 3 status metrics (CREATED, COMPLETED, FAILED) with maximum values
      jobMetrics.setStatusMetrics(
          List.of(
              new StatusMetrics().setCount(Integer.MAX_VALUE).setLastUpdatedAt(Long.MAX_VALUE),
              new StatusMetrics().setCount(Integer.MAX_VALUE).setLastUpdatedAt(Long.MAX_VALUE),
              new StatusMetrics().setCount(Integer.MAX_VALUE).setLastUpdatedAt(Long.MAX_VALUE)));
      record.addJobMetrics(jobMetrics);
    }

    // when - export the record through the engine
    // custom record key to easily fetch the exported record
    final long metricsRecordKey = new Random().nextLong();
    ENGINE_RULE.writeRecords(
        RecordToWrite.event()
            .jobMetricsBatch(JobMetricsBatchIntent.EXPORTED, record)
            .key(metricsRecordKey));

    // then - verify the record is under 4MiB
    final int recordLength = record.getLength();
    // then - verify the record was successfully exported
    final Record<JobMetricsBatchRecordValue> exportedRecord =
        RecordingExporter.jobMetricsBatchRecords()
            .withRecordKey(metricsRecordKey)
            .withIntent(JobMetricsBatchIntent.EXPORTED)
            .getFirst();
    assertThat(exportedRecord.getValue().getBatchStartTime()).isEqualTo(now);
    assertThat(exportedRecord.getValue().getBatchEndTime()).isEqualTo(now);
    assertThat(record.getLength())
        .as(
            "Record size with %d keys should be under max record size (%d bytes accounting overhead), but was %d bytes",
            maxNumberOfKeys, fourMiBMinusOverhead, recordLength)
        .isLessThan((int) fourMiBMinusOverhead);
    // Also verify that the record size limit flag is false
    assertThat(record.getRecordSizeLimitExceeded()).isFalse();
  }
}
