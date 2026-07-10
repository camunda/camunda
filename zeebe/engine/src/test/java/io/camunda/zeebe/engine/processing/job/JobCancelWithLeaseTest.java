/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Proves that terminating the process instance of a job that was activated with a lease still
 * cancels the job, i.e. the engine-internal CANCEL command must never be fenced by a lease token.
 */
public final class JobCancelWithLeaseTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static String jobType;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setup() {
    jobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldCancelLeasedJobWithoutLeaseWhenProcessInstanceIsTerminated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<JobBatchRecordValue> batch =
        ENGINE.jobs().withType(jobType).withLease().activate();
    final long jobKey = batch.getValue().getJobKeys().get(0);
    final String leaseToken = batch.getValue().getJobs().get(0).getLeaseToken();
    assertThat(leaseToken).describedAs("A leased job has a non-empty lease token").isNotEmpty();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final Record<JobRecordValue> canceledRecord =
        RecordingExporter.jobRecords(JobIntent.CANCELED).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(canceledRecord)
        .describedAs("cancel is engine-internal and must never be fenced by a lease token")
        .hasRecordType(RecordType.EVENT)
        .hasIntent(JobIntent.CANCELED);
  }
}
