/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class ThrowErrorTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  private static final String ERROR_CODE = "error";
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;
  private long jobKey;

  @Before
  public void init() {
    jobType = helper.getJobType();

    final var processDefinitionKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .boundaryEvent("error", b -> b.error(ERROR_CODE).endEvent())
                .endEvent()
                .done());

    CLIENT_RULE.createProcessInstance(processDefinitionKey);

    jobKey = activateJob().getKey();
  }

  @Test
  public void shouldThrowError() {
    // when
    CLIENT_RULE.getClient().newThrowErrorCommand(jobKey).errorCode(ERROR_CODE).send().join();

    // then
    final Record<JobRecordValue> record =
        jobRecords(JobIntent.ERROR_THROWN).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(record.getValue()).hasErrorCode(ERROR_CODE).hasErrorMessage("");

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(record.getValue().getProcessInstanceKey())
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.BOUNDARY_EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldThrowErrorWithErrorMessage() {
    // when
    CLIENT_RULE
        .getClient()
        .newThrowErrorCommand(jobKey)
        .errorCode(ERROR_CODE)
        .errorMessage("test")
        .send()
        .join();

    // then
    final Record<JobRecordValue> record =
        jobRecords(JobIntent.ERROR_THROWN).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(record.getValue()).hasErrorMessage("test");
  }

  @Test
  public void shouldRejectIfJobIsAlreadyCompleted() {
    // given
    CLIENT_RULE.getClient().newCompleteCommand(jobKey).send().join();

    // when
    final var expectedMessage =
        String.format(
            "Expected to throw an error for job with key '%d', but no such job was found", jobKey);

    assertThatThrownBy(
            () ->
                CLIENT_RULE
                    .getClient()
                    .newThrowErrorCommand(jobKey)
                    .errorCode(ERROR_CODE)
                    .send()
                    .join())
        .isInstanceOf(ClientStatusException.class)
        .hasMessageContaining(expectedMessage);
  }

  private ActivatedJob activateJob() {
    final var activateResponse =
        CLIENT_RULE
            .getClient()
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join();

    assertThat(activateResponse.getJobs())
        .describedAs("Expected one job to be activated")
        .hasSize(1);

    return activateResponse.getJobs().get(0);
  }
}
