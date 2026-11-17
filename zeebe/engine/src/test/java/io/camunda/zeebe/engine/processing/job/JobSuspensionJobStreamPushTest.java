/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Covers the job-stream push path for a suspended and resumed process instance. A job stream is
 * push-only, so a stream worker learns about a parked job only from the {@code publishWork} call
 * that the {@code RESUME_JOBS} loop in {@code ProcessInstanceResumeJobsProcessor} makes for each
 * un-parked {@code Job.RESUMED}. This needs its own {@link EngineRule} with a {@link
 * RecordingJobStreamer}, so it lives apart from {@code JobSuspensionHandoutTest}.
 */
public final class JobSuspensionJobStreamPushTest {

  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldNotPushSuspendedJobButPushItAfterResume() {
    // given - a job that reaches ACTIVATABLE with no stream registered yet, so it is not
    // immediately activated and pushed on creation
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - the instance is suspended, parking the still-activatable job
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    final DirectBuffer worker = BufferUtil.wrapString("test");
    final var jobActivationProperties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(30_000L)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .setFetchVariables(Set.of(new StringValue("a")));
    final RecordingJobStream jobStream =
        JOB_STREAMER.addJobStream(BufferUtil.wrapString(jobType), jobActivationProperties);

    // then - the parked job is not pushed to a stream waiting for its type
    assertThat(jobStream.getActivatedJobs()).isEmpty();

    // when - the instance is resumed
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - the RESUME_JOBS cycle that un-parks this job also publishes it
    await("push after resume")
        .untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
  }
}
