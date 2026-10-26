/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import static io.camunda.zeebe.test.util.record.RecordingExporter.incidentRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.processInstanceRecords;
import static io.camunda.zeebe.test.util.record.RecordingExporter.records;
import static io.camunda.zeebe.test.util.record.RecordingExporter.variableRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobResultType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A process instance created with stubbed call activities starts none of the processes it calls:
 * each call activity waits on a job that stands in for the called process, so a recording tool can
 * decide what that process would have returned while other instances on the cluster keep calling it
 * for real.
 */
public final class StubbedCallActivityTest {

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
  private String childJobType;

  @Before
  public void setup() {
    parentProcessId = Strings.newRandomValidBpmnId();
    childProcessId = Strings.newRandomValidBpmnId();
    childJobType = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldNotStartCalledProcess() {
    // given
    deployParentAndChild(c -> {});

    // when
    final long processInstanceKey = createStubbedInstance();

    // then
    final long jobKey = stubJob(processInstanceKey).getKey();
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .exists())
        .describedAs("the call activity itself is activated and waits")
        .isTrue();

    ENGINE.job().withKey(jobKey).complete();
    assertThat(records().limitToProcessInstance(processInstanceKey).processInstanceRecords())
        .describedAs("a stubbed call activity starts no instance of the process it calls")
        .noneMatch(r -> childProcessId.equals(r.getValue().getBpmnProcessId()));
  }

  @Test
  public void shouldStartCalledProcessWithoutTheFlag() {
    // given
    deployParentAndChild(c -> {});

    // when
    ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    assertThat(jobRecords(JobIntent.CREATED).withType(childJobType).exists())
        .describedAs("an ordinary instance calls the process for real")
        .isTrue();
  }

  @Test
  public void shouldTellTheStubJobWhichProcessIsStoodInFor() {
    // given
    deployParentAndChild(c -> {});

    // when
    final long processInstanceKey = createStubbedInstance();

    // then
    final Record<JobRecordValue> job = stubJob(processInstanceKey);
    assertThat(job.getValue().getElementType())
        .describedAs("the stub job belongs to the call activity")
        .isEqualTo(BpmnElementType.CALL_ACTIVITY);
    assertThat(job.getValue().getCustomHeaders())
        .describedAs("the stub job names the process it stands in for")
        .containsEntry(Protocol.CALLED_PROCESS_ID_HEADER_NAME, childProcessId);
  }

  @Test
  public void shouldStubCallActivityOfUndeployedProcess() {
    // given only the parent process is deployed
    ENGINE.deployment().withXmlResource("parent.bpmn", parentProcess(c -> {})).deploy();

    // when
    final long processInstanceKey = createStubbedInstance();

    // then
    assertThat(stubJob(processInstanceKey).getValue().getCustomHeaders())
        .describedAs("the process a stubbed call activity calls does not have to be deployed")
        .containsEntry(Protocol.CALLED_PROCESS_ID_HEADER_NAME, childProcessId);
  }

  @Test
  public void shouldNotActivateStubJob() {
    // given
    deployParentAndChild(c -> {});
    createStubbedInstance();

    // when
    final var batch = ENGINE.jobs().withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE).activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("a poll must not be served a call activity stub job")
        .isEmpty();
  }

  @Test
  public void shouldNotActivateStubJobWithLease() {
    // given
    deployParentAndChild(c -> {});
    createStubbedInstance();

    // when a lease-aware worker polls, the way an agent worker does
    final var batch =
        ENGINE.jobs().withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE).withLease().activate();

    // then
    assertThat(batch.getValue().getJobs())
        .describedAs("opting into leases does not unlock a call activity stub job")
        .isEmpty();
  }

  @Test
  public void shouldNotPushStubJobToStream() {
    // given a stream is waiting for the stub job type before the instance exists
    deployParentAndChild(c -> {});
    final RecordingJobStream jobStream = registerStream();

    // when
    final long processInstanceKey = createStubbedInstance();
    stubJob(processInstanceKey);

    // then
    assertThat(jobStream.getActivatedJobs())
        .describedAs("a call activity stub job is never pushed to a stream")
        .isEmpty();
  }

  @Test
  public void shouldCompleteCallActivityOnStubJobCompletion() {
    // given
    deployParentAndChild(c -> {});
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when
    ENGINE.job().withKey(jobKey).complete();

    // then
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("completing the stub job completes the call activity and the instance")
        .isTrue();
  }

  @Test
  public void shouldPropagateStubJobVariables() {
    // given a call activity that propagates what the called process returns
    deployParentAndChild(c -> c.zeebePropagateAllChildVariables(true));
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when
    ENGINE.job().withKey(jobKey).withVariables(Map.of("result", "ok")).complete();

    // then
    assertThat(
            variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withName("result")
                .exists())
        .describedAs("the stub job's variables reach the parent, as the called process's would")
        .isTrue();
  }

  @Test
  public void shouldApplyOutputMappingsToStubJobVariables() {
    // given
    deployParentAndChild(
        c -> c.zeebePropagateAllChildVariables(false).zeebeOutputExpression("result", "mapped"));
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when
    ENGINE.job().withKey(jobKey).withVariables(Map.of("result", "ok")).complete();

    // then
    assertThat(
            variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withName("mapped")
                .exists())
        .describedAs("the call activity's output mappings are applied to the stub job's variables")
        .isTrue();
  }

  @Test
  public void shouldNotPropagateStubJobVariablesWhenTheCalledProcessWouldNot() {
    // given a call activity that propagates nothing of what the called process returns
    deployParentAndChild(c -> c.zeebePropagateAllChildVariables(false));
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when
    ENGINE.job().withKey(jobKey).withVariables(Map.of("result", "ok")).complete();

    // then
    assertThat(records().limitToProcessInstance(processInstanceKey).variableRecords())
        .describedAs("stubbing a called process does not start propagating what it returns")
        .noneMatch(r -> "result".equals(r.getValue().getName()));
  }

  @Test
  public void shouldTerminateStubbedCallActivityOnCancellation() {
    // given
    deployParentAndChild(c -> {});
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(jobRecords(JobIntent.CANCELED).withRecordKey(jobKey).exists())
        .describedAs("cancelling the instance cancels the stub job")
        .isTrue();
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.CALL_ACTIVITY)
                .exists())
        .describedAs("a stubbed call activity terminates although it has no child instance")
        .isTrue();
  }

  @Test
  public void shouldInterruptStubbedCallActivityWithBoundaryEvent() {
    // given a call activity with an interrupting message boundary event
    final String messageName = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            "parent.bpmn",
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .boundaryEvent(
                    "boundary",
                    b ->
                        b.cancelActivity(true)
                            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withStubbedCallActivities()
            .withVariable("key", "key-1")
            .create();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when
    ENGINE.message().withName(messageName).withCorrelationKey("key-1").publish();

    // then
    assertThat(jobRecords(JobIntent.CANCELED).withRecordKey(jobKey).exists())
        .describedAs("interrupting the call activity cancels its stub job")
        .isTrue();
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("the boundary event takes over and the instance finishes")
        .isTrue();
  }

  @Test
  public void shouldRetainStubbingAfterReplay() {
    // given
    deployParentAndChild(c -> {});
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when
    ENGINE.replay();
    ENGINE.job().withKey(jobKey).complete();

    // then
    assertThat(records().limitToProcessInstance(processInstanceKey).processInstanceRecords())
        .describedAs("the stubbing survives log replay")
        .noneMatch(r -> childProcessId.equals(r.getValue().getBpmnProcessId()));
  }

  @Test
  public void shouldStartCalledProcessOnRequest() {
    // given
    deployParentAndChild(c -> {});
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when the completer asks for the real process instead of standing in for it
    ENGINE.job().withKey(jobKey).withResult(runCalledProcess()).complete();

    // then
    final var childJob = jobRecords(JobIntent.CREATED).withType(childJobType).getFirst();
    assertThat(childJob.getValue().getProcessInstanceKey())
        .describedAs("the process the call activity calls is started for real")
        .isNotEqualTo(processInstanceKey);

    ENGINE.job().withKey(childJob.getKey()).complete();
    assertThat(
            processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("the called process completes the call activity as it always would")
        .isTrue();
  }

  @Test
  public void shouldNotPropagateStubJobVariablesWhenStartingCalledProcess() {
    // given a call activity that propagates what the called process returns
    deployParentAndChild(c -> c.zeebePropagateAllChildVariables(true));
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();

    // when
    ENGINE
        .job()
        .withKey(jobKey)
        .withVariables(Map.of("stub", "leaked"))
        .withResult(runCalledProcess())
        .complete();
    final var childJob = jobRecords(JobIntent.CREATED).withType(childJobType).getFirst();
    ENGINE.job().withKey(childJob.getKey()).complete();

    // then
    assertThat(records().limitToProcessInstance(processInstanceKey).variableRecords())
        .describedAs("the real called process produces the variables, not the stub job")
        .noneMatch(r -> "stub".equals(r.getValue().getName()));
  }

  @Test
  public void shouldStubCallActivitiesOfStartedCalledProcess() {
    // given a child process that itself calls a grandchild process
    final String grandchildProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess(c -> {}))
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .callActivity("child-call", c -> c.zeebeProcessId(grandchildProcessId))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = createStubbedInstance();

    // when
    ENGINE
        .job()
        .withKey(stubJob(processInstanceKey).getKey())
        .withResult(runCalledProcess())
        .complete();

    // then
    assertThat(
            jobRecords(JobIntent.CREATED)
                .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
                .limit(2)
                .count())
        .describedAs("the started process inherits the stubbing, so its own call activity stubs")
        .isEqualTo(2);
  }

  @Test
  public void shouldRaiseResolvableIncidentWhenCalledProcessIsNotDeployed() {
    // given only the parent process is deployed
    ENGINE.deployment().withXmlResource("parent.bpmn", parentProcess(c -> {})).deploy();
    final long processInstanceKey = createStubbedInstance();

    // when the real process is asked for although it is not deployed
    ENGINE
        .job()
        .withKey(stubJob(processInstanceKey).getKey())
        .withResult(runCalledProcess())
        .complete();

    // then
    final var incident =
        incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incident.getValue().getErrorType())
        .describedAs("asking for a process that is not deployed raises the ordinary incident")
        .isEqualTo(ErrorType.CALLED_ELEMENT_ERROR);

    // and it is resolvable once the process is deployed
    ENGINE
        .deployment()
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("child-task", t -> t.zeebeJobType(childJobType))
                .endEvent()
                .done())
        .deploy();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    assertThat(jobRecords(JobIntent.CREATED).withType(childJobType).exists())
        .describedAs("resolving the incident starts the process that is now deployed")
        .isTrue();
  }

  @Test
  public void shouldRejectRunCalledProcessForAnOrdinaryJob() {
    // given an ordinary service task job
    deployParentAndChild(c -> {});
    ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final long jobKey = jobRecords(JobIntent.CREATED).withType(childJobType).getFirst().getKey();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(jobKey).withResult(runCalledProcess()).expectRejection().complete();

    // then
    assertThat(rejection.getRejectionType())
        .describedAs("only a job standing in for a called process can ask for the real one")
        .isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectStubJobCompletionAfterCancellation() {
    // given
    deployParentAndChild(c -> {});
    final long processInstanceKey = createStubbedInstance();
    final long jobKey = stubJob(processInstanceKey).getKey();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // when
    final Record<JobRecordValue> rejection =
        ENGINE.job().withKey(jobKey).withResult(runCalledProcess()).expectRejection().complete();

    // then
    assertThat(rejection.getRejectionType())
        .describedAs("a cancelled instance's stub job cannot start the called process afterwards")
        .isEqualTo(RejectionType.NOT_FOUND);
  }

  private static JobResult runCalledProcess() {
    return new JobResult().setType(JobResultType.CALL_ACTIVITY).setRunCalledProcess(true);
  }

  private BpmnModelInstance parentProcess(final Consumer<CallActivityBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId(childProcessId));
    consumer.accept(builder);
    return builder.endEvent().done();
  }

  private void deployParentAndChild(final Consumer<CallActivityBuilder> consumer) {
    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess(consumer))
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("child-task", t -> t.zeebeJobType(childJobType))
                .endEvent()
                .done())
        .deploy();
  }

  private long createStubbedInstance() {
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(parentProcessId)
        .withStubbedCallActivities()
        .create();
  }

  private Record<JobRecordValue> stubJob(final long processInstanceKey) {
    return jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(Protocol.CALL_ACTIVITY_STUB_JOB_TYPE)
        .getFirst();
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
}
