/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.TIMED_OUT;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public final class ActivatableJobsNotificationTests {

  private static final String PROCESS_ID = "process";
  private static final Function<String, BpmnModelInstance> MODEL_SUPPLIER =
      (type) ->
          Bpmn.createExecutableProcess(PROCESS_ID)
              .startEvent("start")
              .serviceTask("task", b -> b.zeebeJobType(type).done())
              .endEvent("end")
              .done();

  private static final GatewayStreamer<JobActivationProperties, ActivatedJob>
      JOB_AVAILABLE_CALLBACK =
          (GatewayStreamer<JobActivationProperties, ActivatedJob>)
              Mockito.spy(GatewayStreamer.class);

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_AVAILABLE_CALLBACK);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String taskType;

  @Before
  public void setup() {
    taskType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(PROCESS_ID + ".bpmn", MODEL_SUPPLIER.apply(taskType))
        .deploy();
  }

  @Test
  public void shouldNotifyWhenJobCreated() {
    // when
    createProcessInstanceAndJobs(3);

    // then
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(3))
        .streamFor(argThat(new TaskTypeMatcher(taskType)));
  }

  @Test
  public void shouldNotifyWhenJobsAvailableAgain() {
    // given
    createProcessInstanceAndJobs(1);
    final Record<JobBatchRecordValue> jobs = activateJobs(1);

    // when
    createProcessInstanceAndJobs(1);

    // then
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(2))
        .streamFor(argThat(new TaskTypeMatcher(taskType)));
  }

  @Test
  public void shouldNotifyWhenJobCanceled() {
    // given
    final List<Long> instanceKeys = createProcessInstanceAndJobs(1);
    ENGINE.processInstance().withInstanceKey(instanceKeys.get(0)).cancel();

    // when
    createProcessInstanceAndJobs(1);

    // then
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(2))
        .streamFor(argThat(new TaskTypeMatcher(taskType)));
  }

  @Test
  public void shouldNotifyWhenJobsAvailableAfterTimeOut() {
    // given
    createProcessInstanceAndJobs(1);
    activateJobs(1, Duration.ofMillis(10));

    // when
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(TIMED_OUT).withType(taskType).getFirst();

    // then
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(2))
        .streamFor(argThat(new TaskTypeMatcher(taskType)));
  }

  @Test
  public void shouldNotifyWhenJobCreatedAfterNotActivatedJobCompleted() {
    // given
    createProcessInstanceAndJobs(1);
    final long jobKey = activateJobs(1, Duration.ofMillis(10)).getValue().getJobKeys().get(0);
    ENGINE.increaseTime(JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL);
    RecordingExporter.jobRecords(TIMED_OUT).withType(taskType).getFirst();

    // when
    ENGINE.job().withKey(jobKey).complete();
    createProcessInstanceAndJobs(1);

    // then
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(3))
        .streamFor(argThat(new TaskTypeMatcher(taskType)));
  }

  @Test
  public void shouldNotifyWhenJobsFailWithRetryAvailable() {
    // given
    createProcessInstanceAndJobs(1);
    final Record<JobBatchRecordValue> jobs = activateJobs(1);
    final long jobKey = jobs.getValue().getJobKeys().get(0);

    // when
    ENGINE.job().withKey(jobKey).withRetries(10).fail();

    // then
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(2))
        .streamFor(argThat(new TaskTypeMatcher(taskType)));
  }

  @Test
  public void shouldNotifyWhenFailedJobsResolved() {
    // given
    createProcessInstanceAndJobs(1);
    final Record<JobBatchRecordValue> jobs = activateJobs(1);
    final JobRecordValue job = jobs.getValue().getJobs().get(0);

    ENGINE.job().withType(taskType).ofInstance(job.getProcessInstanceKey()).fail();

    // when
    ENGINE
        .job()
        .ofInstance(job.getProcessInstanceKey())
        .withType(taskType)
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(job.getProcessInstanceKey()).resolve();

    // then
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(2))
        .streamFor(argThat(new TaskTypeMatcher(taskType)));
  }

  @Test
  public void shouldNotifyForMultipleJobTypes() {
    // given
    final String firstType = Strings.newRandomValidBpmnId();
    final String secondType = Strings.newRandomValidBpmnId();

    // when
    ENGINE.createJob(firstType, PROCESS_ID);
    ENGINE.createJob(secondType, PROCESS_ID);

    // then
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(1))
        .streamFor(argThat(new TaskTypeMatcher(firstType)));
    Mockito.verify(JOB_AVAILABLE_CALLBACK, times(1))
        .streamFor(argThat(new TaskTypeMatcher(secondType)));
  }

  private List<Long> createProcessInstanceAndJobs(final int amount) {
    return IntStream.range(0, amount)
        .mapToObj(i -> ENGINE.createJob(taskType, PROCESS_ID))
        .map(r -> r.getValue().getProcessInstanceKey())
        .collect(Collectors.toList());
  }

  private Record<JobBatchRecordValue> activateJobs(final int amount) {
    final Duration timeout = Duration.ofMinutes(12);
    return activateJobs(amount, timeout);
  }

  private Record<JobBatchRecordValue> activateJobs(final int amount, final Duration timeout) {
    final String worker = "myTestWorker";
    return ENGINE
        .jobs()
        .withType(taskType)
        .byWorker(worker)
        .withTimeout(timeout.toMillis())
        .withMaxJobsToActivate(amount)
        .activate();
  }

  private record TaskTypeMatcher(String expected) implements ArgumentMatcher<DirectBuffer> {

    @Override
    public boolean matches(final DirectBuffer actual) {
      return expected.equals(BufferUtil.bufferAsString(actual));
    }
  }
}
