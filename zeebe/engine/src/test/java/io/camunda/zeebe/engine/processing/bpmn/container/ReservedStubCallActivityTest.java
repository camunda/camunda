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
            .withReservationToken(TOKEN)
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
    ENGINE.job().withKey(jobKey).withReservationToken(TOKEN).complete();

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
            .withReservationToken("someone-elses-token")
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
            .withReservedJobTypes(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withReservationToken(TOKEN)
            .create();
    awaitStubJob(processInstanceKey);

    // when draining only the first called process
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withReservationToken(TOKEN)
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
            .withReservedJobTypes(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withReservationToken(TOKEN)
            .create();
    awaitStubJob(processInstanceKey);

    // when
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withReservationToken(TOKEN)
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

  @Test
  public void shouldNotReserveAnUntokenedStubEvenWhenOtherJobTypesAreReserved() {
    // given an instance that reserves a real job type but stubs without listing the stub type
    final String reservedJobType = Strings.newRandomValidBpmnId();
    deploy(parentCalling(childProcessId));
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withStubbedCallActivities(childProcessId)
            .withReservedJobTypes(reservedJobType)
            .withReservationToken(TOKEN)
            .create();
    awaitStubJob(processInstanceKey);

    // when / then — the stub is not among the reserved types, so it carries no token
    assertThat(
            jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
                .getFirst()
                .getValue()
                .getReservationToken())
        .describedAs("an un-reserved stub carries no token even when other job types are reserved")
        .isEmpty();
    assertThat(
            ENGINE
                .jobs()
                .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
                .withReservationToken(TOKEN)
                .withCalledProcessId(childProcessId)
                .activate()
                .getValue()
                .getJobs())
        .describedAs("and so it is served to no worker, token or not")
        .isEmpty();
  }

  @Test
  public void shouldDriveTheErrorBoundaryWhenTheReservedStubThrows() {
    // given a call activity with an error boundary event, stubbed and reserved
    final String errorCode = "CHILD_FAILED";
    deploy(
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId(childProcessId))
            .boundaryEvent("err", b -> b.error(errorCode))
            .endEvent("errorEnd")
            .moveToActivity("call")
            .endEvent()
            .done());
    final long processInstanceKey = createReservedStubbedInstance(childProcessId);
    final long jobKey = awaitStubJob(processInstanceKey);

    // when the mock worker throws from the stub
    ENGINE.job().withKey(jobKey).withReservationToken(TOKEN).withErrorCode(errorCode).throwError();

    // then the call activity's error boundary event completes the instance
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("errorEnd")
                .exists())
        .describedAs("throwing from the reserved stub drives the call activity's error boundary")
        .isTrue();
  }

  private long createReservedStubbedInstance(final String... processIds) {
    // the gateway mapper lists the stub job type among the reserved types when the stub switch
    // carries a token; an engine test bypasses the mapper, so it lists the type itself
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(parentProcessId)
        .withStubbedCallActivities(processIds)
        .withReservedJobTypes(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
        .withReservationToken(TOKEN)
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
