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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A mock worker that names the called process id drains the stub jobs of the call activities
 * calling that process, without a reservation token, while the stubs of every other called process
 * are left for their own mock workers. One activation drains a whole multi-instance call activity,
 * whose iteration count is only known at runtime.
 */
public final class CalledProcessStubActivationTest {

  private static final String TOKEN = "ts-stub";

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

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
  public void shouldServeStubToAnActivationNamingItsCalledProcess() {
    // given
    deploy(parentCalling(childProcessId));
    final long processInstanceKey = createStubbedInstance(childProcessId);
    awaitStubJob(processInstanceKey);

    // when
    final var batch = activateStubsOf(childProcessId);

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("the mock worker naming the called process drains the stub without a token")
        .singleElement()
        .satisfies(job -> assertThat(job.getReservationToken()).isEmpty());
  }

  @Test
  public void shouldCompleteTheCallActivityWhenTheStubIsCompleted() {
    // given
    deploy(parentCalling(childProcessId));
    final long processInstanceKey = createStubbedInstance(childProcessId);
    final long jobKey = awaitStubJob(processInstanceKey);

    // when
    ENGINE.job().withKey(jobKey).complete();

    // then
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .exists())
        .describedAs("completing the stub completes the call activity")
        .isTrue();
  }

  @Test
  public void shouldServeOnlyTheStubsOfTheRequestedCalledProcess() {
    // given a parent that stubs two different called processes
    final String otherChildId = Strings.newRandomValidBpmnId();
    deploy(
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .parallelGateway("fork")
            .callActivity("callA", c -> c.zeebeProcessId(childProcessId))
            .endEvent()
            .moveToNode("fork")
            .callActivity("callB", c -> c.zeebeProcessId(otherChildId))
            .endEvent()
            .done());
    final long processInstanceKey = createStubbedInstance(childProcessId, otherChildId);
    awaitStubJobs(processInstanceKey, 2);

    // when draining only the first called process
    final var batch = activateStubsOf(childProcessId);

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
            .create();
    awaitStubJobs(processInstanceKey, 3);

    // when
    final var batch = activateStubsOf(childProcessId);

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("one activation drains every iteration's stub job")
        .hasSize(3);
  }

  @Test
  public void shouldNotReserveTheStubWhenOtherJobTypesAreReserved() {
    // given an instance that reserves a real job type and stubs a call activity
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
    final long jobKey = awaitStubJob(processInstanceKey);

    // when the run's mock worker, which presents the run's token, drains the stub
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
            .withReservationToken(TOKEN)
            .withCalledProcessId(childProcessId)
            .activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("the stub carries no token, so the mock worker is served it regardless")
        .singleElement()
        .satisfies(job -> assertThat(job.getReservationToken()).isEmpty());
    ENGINE.job().withKey(jobKey).complete();
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .exists())
        .describedAs("and the stub is completed without a token")
        .isTrue();
  }

  @Test
  public void shouldDriveTheErrorBoundaryWhenTheStubThrows() {
    // given a call activity with an error boundary event, stubbed
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
    final long processInstanceKey = createStubbedInstance(childProcessId);
    final long jobKey = awaitStubJob(processInstanceKey);

    // when the mock worker throws from the stub
    ENGINE.job().withKey(jobKey).withErrorCode(errorCode).throwError();

    // then the call activity's error boundary event completes the instance
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("errorEnd")
                .exists())
        .describedAs("throwing from the stub drives the call activity's error boundary")
        .isTrue();
  }

  private long createStubbedInstance(final String... processIds) {
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(parentProcessId)
        .withStubbedCallActivities(processIds)
        .create();
  }

  private Record<JobBatchRecordValue> activateStubsOf(final String calledProcessId) {
    return ENGINE
        .jobs()
        .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
        .withCalledProcessId(calledProcessId)
        .activate();
  }

  private long awaitStubJob(final long processInstanceKey) {
    return awaitStubJobs(processInstanceKey, 1);
  }

  private long awaitStubJobs(final long processInstanceKey, final int count) {
    return jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
        .limit(count)
        .toList()
        .getLast()
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
