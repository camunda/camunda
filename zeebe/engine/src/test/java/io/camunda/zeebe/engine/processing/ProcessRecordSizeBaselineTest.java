/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordSizeBaseline;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Checks the log bytes of single-partition scenarios. See {@link RecordSizeBaseline}. */
public final class ProcessRecordSizeBaselineTest {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "task";
  private static final AuthInfo CLAIMS = RecordSizeBaseline.oidcClaims("demo");

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldNotChangeRecordSizesOfServiceTask() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done());
    final var startPositions = RecordSizeBaseline.lastPositions(engine);

    // when
    final long processInstanceKey = createProcessInstance();
    completeJob(jobKey(processInstanceKey, JOB_TYPE));
    awaitCompletion(processInstanceKey);

    // then
    RecordSizeBaseline.assertMatches(engine, "service-task", startPositions);
  }

  @Test
  public void shouldNotChangeRecordSizesOfUserTaskWithTaskListener() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeTaskListener(l -> l.completing().type("listener"))
            .endEvent()
            .done());
    final var startPositions = RecordSizeBaseline.lastPositions(engine);

    // when
    final long processInstanceKey = createProcessInstance();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    engine.writeRecords(
        RecordToWrite.commandFromRequest(CLAIMS)
            .userTask(UserTaskIntent.COMPLETE, new UserTaskRecord().setUserTaskKey(userTaskKey))
            .key(userTaskKey));
    completeJob(jobKey(processInstanceKey, "listener"));
    awaitCompletion(processInstanceKey);

    // then
    RecordSizeBaseline.assertMatches(engine, "user-task-with-task-listener", startPositions);
  }

  @Test
  public void shouldNotChangeRecordSizesOfMessageCorrelation() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent(
                "message", e -> e.message(m -> m.name("message").zeebeCorrelationKey("=key")))
            .endEvent()
            .done());
    final var startPositions = RecordSizeBaseline.lastPositions(engine);

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-1")
            .create(CLAIMS);
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    engine.writeRecords(
        RecordToWrite.commandFromRequest(CLAIMS)
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord()
                    .setName("message")
                    .setCorrelationKey("order-1")
                    .setTimeToLive(0)));
    awaitCompletion(processInstanceKey);

    // then
    RecordSizeBaseline.assertMatches(engine, "message-correlation", startPositions);
  }

  @Test
  public void shouldNotChangeRecordSizesOfIncidentResolution() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done());
    final var startPositions = RecordSizeBaseline.lastPositions(engine);

    // when
    final long processInstanceKey = createProcessInstance();
    final long jobKey = jobKey(processInstanceKey, JOB_TYPE);
    writeJobCommand(jobKey, JobIntent.FAIL, new JobRecord().setRetries(0).setErrorMessage("fail"));
    final long incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    writeJobCommand(jobKey, JobIntent.UPDATE_RETRIES, new JobRecord().setRetries(1));
    engine.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve(CLAIMS);
    completeJob(jobKey);
    awaitCompletion(processInstanceKey);

    // then
    RecordSizeBaseline.assertMatches(engine, "incident-resolution", startPositions);
  }

  @Test
  public void shouldNotChangeRecordSizesOfTimer() {
    // given
    deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT1M"))
            .endEvent()
            .done());
    final var startPositions = RecordSizeBaseline.lastPositions(engine);

    // when
    final long processInstanceKey = createProcessInstance();
    RecordingExporter.timerRecords(TimerIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    engine.increaseTime(Duration.ofMinutes(1));
    awaitCompletion(processInstanceKey);

    // then
    RecordSizeBaseline.assertMatches(engine, "timer", startPositions);
  }

  @Test
  public void shouldNotChangeRecordSizesOfRejections() {
    // given
    final var startPositions = RecordSizeBaseline.lastPositions(engine);

    // when
    engine.processInstance().ofBpmnProcessId("unknown").expectRejection().create(CLAIMS);
    writeJobCommand(1L, JobIntent.COMPLETE, new JobRecord());
    RecordingExporter.jobRecords(JobIntent.COMPLETE).onlyCommandRejections().await();

    // then
    RecordSizeBaseline.assertMatches(engine, "rejections", startPositions);
  }

  private void deploy(final BpmnModelInstance process) {
    engine.deployment().withXmlResource(process).deploy();
  }

  private long createProcessInstance() {
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(CLAIMS);
  }

  private static long jobKey(final long processInstanceKey, final String type) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(type)
        .getFirst()
        .getKey();
  }

  private void completeJob(final long jobKey) {
    writeJobCommand(jobKey, JobIntent.COMPLETE, new JobRecord());
  }

  /** Writes the job command as a worker sends it: with the claims of an OIDC client. */
  private void writeJobCommand(final long jobKey, final JobIntent intent, final JobRecord value) {
    engine.writeRecords(RecordToWrite.commandFromRequest(CLAIMS).job(intent, value).key(jobKey));
  }

  private static void awaitCompletion(final long processInstanceKey) {
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }
}
