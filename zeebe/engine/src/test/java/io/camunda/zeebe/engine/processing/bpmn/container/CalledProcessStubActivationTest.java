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

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String TOKEN = "ts-stub";

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
    awaitStubJob(processInstanceKey, childProcessId);

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
    final long jobKey = awaitStubJob(processInstanceKey, childProcessId);

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
    awaitStubJob(processInstanceKey, childProcessId);
    awaitStubJob(processInstanceKey, otherChildId);

    // when draining the other called process' composite type
    final var otherBatch = activateStubsOf(otherChildId);

    // then only that child's stub is served, never this child's
    assertThat(otherBatch.getValue().getJobs())
        .describedAs("the other child's composite type serves only the other child's stub")
        .singleElement()
        .extracting(j -> j.getCustomHeaders().get(Protocol.CALLED_PROCESS_ID_HEADER_NAME))
        .isEqualTo(otherChildId);

    // when activating an ordinary user job type
    final var userBatch = ENGINE.jobs().withType(Strings.newRandomValidBpmnId()).activate();

    // then no stub is served
    assertThat(userBatch.getValue().getJobs())
        .describedAs("an ordinary user job type is not served any stub")
        .isEmpty();

    // when draining this child's composite type, untouched by the activations above
    final var batch = activateStubsOf(childProcessId);

    // then its stub is still served
    assertThat(batch.getValue().getJobs())
        .describedAs("the requested child's composite type still serves its own stub")
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
    awaitStubJobs(processInstanceKey, childProcessId, 3);

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
    final long jobKey = awaitStubJob(processInstanceKey, childProcessId);

    // when the run's mock worker, which presents the run's token, drains the stub
    final var batch =
        ENGINE
            .jobs()
            .withType(Protocol.callActivityStubJobType(childProcessId))
            .withReservationToken(TOKEN)
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
    final long jobKey = awaitStubJob(processInstanceKey, childProcessId);

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
    return ENGINE.jobs().withType(Protocol.callActivityStubJobType(calledProcessId)).activate();
  }

  private long awaitStubJob(final long processInstanceKey, final String calledProcessId) {
    return awaitStubJobs(processInstanceKey, calledProcessId, 1);
  }

  private long awaitStubJobs(
      final long processInstanceKey, final String calledProcessId, final int count) {
    return jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(Protocol.callActivityStubJobType(calledProcessId))
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
