/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.protocol.record.intent.JobIntent.ERROR_THROWN;
import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobThrowErrorTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static String jobType;

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Before
  public void setup() {
    jobType = helper.getJobType();
  }

  @Test
  public void shouldThrowError() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    // when
    final Record<JobRecordValue> result =
        ENGINE
            .job()
            .withKey(job.getKey())
            .withErrorCode("error")
            .withErrorMessage("error-message")
            .throwError();

    // then
    Assertions.assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(result.getValue()).hasErrorCode("error").hasErrorMessage("error-message");
  }

  @Test
  public void shouldRejectIfJobNotFound() {
    // given
    final int key = 123;

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(key).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectIfJobIsFailed() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    ENGINE.jobs().withType(jobType).activate();
    ENGINE.job().withKey(job.getKey()).withRetries(0).fail();

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(result.getRejectionReason()).contains("it is in state 'FAILED'");
  }

  @Test
  public void shouldRejectIfErrorIsThrown() {
    // given
    final var job = ENGINE.createJob(jobType, PROCESS_ID);

    ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // when
    final Record<JobRecordValue> result =
        ENGINE.job().withKey(job.getKey()).withErrorCode("error").throwError();

    // then
    Assertions.assertThat(result).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(result.getRejectionReason()).contains("it is in state 'ERROR_THROWN'");
  }

  @Test
  public void shouldThrowErrorIfNoCatchEventFound() {
    // given
    final String processId = "process_with_error_boundary";
    ENGINE
        .deployment()
        .withXmlResource(
            processId,
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .serviceTask("task", b -> b.zeebeJobType(jobType).done())
                .boundaryEvent("error1", b -> b.error("error_message_1"))
                .endEvent()
                .moveToActivity("task")
                .boundaryEvent("error2", b -> b.error("error_message_2"))
                .endEvent("end")
                .done())
        .deploy();

    final long instanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var job =
        jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .filter(r -> r.getValue().getProcessInstanceKey() == instanceKey)
            .getFirst();

    // when
    final Record<JobRecordValue> result =
        ENGINE
            .job()
            .withKey(job.getKey())
            .withErrorCode("error")
            .withErrorMessage("error message")
            .throwError();

    // then
    Assertions.assertThat(result).hasRecordType(RecordType.EVENT).hasIntent(ERROR_THROWN);
    Assertions.assertThat(result.getValue()).hasErrorCode("error").hasErrorMessage("error message");
    Assertions.assertThat(
            RecordingExporter.incidentRecords()
                .withJobKey(job.getKey())
                .withIntent(IncidentIntent.CREATED)
                .getFirst()
                .getValue())
        .hasErrorMessage(
            "An error was thrown with the code 'error' with message 'error message', but not caught. Available error events are [error_message_2, error_message_1]");
  }
}
