/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.processInstanceRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * When a stubbed-call-activity switch carries a reservation token, the stub jobs are reserved by
 * it: a mock worker presenting the token and the called-process id drains them, while every worker
 * without the token — and every stub of another called process — is served none. One activation
 * drains a whole multi-instance call activity, whose iteration count is only known at runtime.
 */
public final class ReservedStubCallActivityTest {

  private static final String TOKEN = "ts-reserved-stub";
  private static final long TIMEOUT_MS = 30_000L;
  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String parentProcessId;
  private String childProcessId;

  @Before
  public void setup() {
    parentProcessId = Strings.newRandomValidBpmnId();
    childProcessId = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldServeReservedStubToTheTokenHolder() {
    // given
    deploy(parentCalling(childProcessId));
    final long processInstanceKey = createReservedStubbedInstance(childProcessId);
    awaitStubJob(processInstanceKey);

    // when
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withJobReservationToken(TOKEN)
            .withCalledProcessId(childProcessId)
            .activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("the mock worker presenting the token and called process drains the stub")
        .hasSize(1);
  }

  @Test
  public void shouldCompleteTheCallActivityWhenTheReservedStubIsCompleted() {
    // given
    deploy(parentCalling(childProcessId));
    final long processInstanceKey = createReservedStubbedInstance(childProcessId);
    final long jobKey = awaitStubJob(processInstanceKey);

    // when
    ENGINE.job().withKey(jobKey).withJobReservationToken(TOKEN).complete();

    // then
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .exists())
        .describedAs("completing the reserved stub completes the call activity")
        .isTrue();
  }

  @Test
  public void shouldNotServeReservedStubToAWorkerWithoutTheToken() {
    // given
    deploy(parentCalling(childProcessId));
    final long processInstanceKey = createReservedStubbedInstance(childProcessId);
    awaitStubJob(processInstanceKey);

    // when
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withCalledProcessId(childProcessId)
            .activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("a worker presenting no reservation token is served no reserved stub")
        .isEmpty();
  }

  @Test
  public void shouldNotServeReservedStubToAWorkerWithTheWrongToken() {
    // given
    deploy(parentCalling(childProcessId));
    final long processInstanceKey = createReservedStubbedInstance(childProcessId);
    awaitStubJob(processInstanceKey);

    // when
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withJobReservationToken("someone-elses-token")
            .withCalledProcessId(childProcessId)
            .activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("a worker presenting a different token is served no reserved stub")
        .isEmpty();
  }

  @Test
  public void shouldServeOnlyTheStubsOfTheRequestedCalledProcess() {
    // given a parent that stubs two different called processes
    final String otherChildId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity("callA", c -> c.zeebeProcessId(childProcessId))
            .callActivity("callB", c -> c.zeebeProcessId(otherChildId))
            .endEvent()
            .done());
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withStubbedCallActivities(childProcessId, otherChildId)
            .withJobReservationToken(TOKEN)
            .create();
    awaitStubJob(processInstanceKey);

    // when draining only the first called process
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withJobReservationToken(TOKEN)
            .withCalledProcessId(childProcessId)
            .activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("the activation drains only the stubs of the requested called process")
        .singleElement()
        .extracting(j -> j.getCustomHeaders().get(Protocol.CALLED_PROCESS_ID_HEADER_NAME))
        .isEqualTo(childProcessId);
  }

  @Test
  public void shouldDrainAMultiInstanceCallActivityWithOneActivation() {
    // given a multi-instance call activity that parks three stub jobs
    deploy(
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c ->
                    c.zeebeProcessId(childProcessId)
                        .multiInstance(
                            m ->
                                m.parallel().zeebeInputCollection("=items").zeebeInputElement("i")))
            .endEvent()
            .done());
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withVariable("items", List.of(1, 2, 3))
            .withStubbedCallActivities(childProcessId)
            .withJobReservationToken(TOKEN)
            .create();
    awaitStubJob(processInstanceKey);

    // when
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withJobReservationToken(TOKEN)
            .withCalledProcessId(childProcessId)
            .activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("one activation drains every iteration's stub job")
        .hasSize(3);
  }

  @Test
  public void shouldKeepAnUntokenedStubHiddenFromEveryWorker() {
    // given a stubbed call activity with no reservation token (v1 behavior)
    deploy(parentCalling(childProcessId));
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withStubbedCallActivities(childProcessId)
            .create();
    awaitStubJob(processInstanceKey);

    // when
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withCalledProcessId(childProcessId)
            .activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("an un-reserved stub is served to no worker, as before")
        .isEmpty();
  }

  @Test
  public void shouldNotPushReservedStubToAStream() {
    // given a stream subscribed to the stub job type (streams carry no reservation token)
    final RecordingJobStream jobStream = registerStream();
    deploy(parentCalling(childProcessId));

    // when
    final long processInstanceKey = createReservedStubbedInstance(childProcessId);
    awaitStubJob(processInstanceKey);

    // then
    assertThat(jobStream.getActivatedJobs())
        .describedAs("a reserved stub is withheld from a job stream, which presents no token")
        .isEmpty();
  }

  private RecordingJobStream registerStream() {
    final var worker = BufferUtil.wrapString("test");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(TIMEOUT_MS)
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
    return JOB_STREAMER.addJobStream(
        BufferUtil.wrapString(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE), properties);
  }

  private long createReservedStubbedInstance(final String... processIds) {
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(parentProcessId)
        .withStubbedCallActivities(processIds)
        .withJobReservationToken(TOKEN)
        .create();
  }

  private long awaitStubJob(final long processInstanceKey) {
    return jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
        .getFirst()
        .getKey();
  }

  private BpmnModelInstance parentCalling(final String calledProcessId) {
    return Bpmn.createExecutableProcess(parentProcessId)
        .startEvent()
        .callActivity("call", c -> c.zeebeProcessId(calledProcessId))
        .endEvent()
        .done();
  }

  private void deploy(final BpmnModelInstance parent) {
    ENGINE.deployment().withXmlResource("parent.bpmn", parent).deploy();
  }
}
