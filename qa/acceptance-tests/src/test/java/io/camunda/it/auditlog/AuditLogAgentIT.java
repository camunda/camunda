/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class AuditLogAgentIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String AGENT_ELEMENT_ID = "test_agent_ahsp";
  private static final String AGENT_JOB_TYPE = "agent-job";
  private static final String EXTERNAL_AGENT_ELEMENT_ID = "external_agent_service_task";
  private static final String EXTERNAL_AGENT_JOB_TYPE = "external-agent-job";
  private static CamundaClient adminClient;

  @BeforeAll
  static void setup(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // Deploy a process with an agent ad-hoc subprocess and complete a job with variables
    final var processModel =
        Bpmn.createExecutableProcess("AGENT_PROCESS")
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("A1"))
            .zeebeJobType(AGENT_JOB_TYPE)
            .zeebeAiAgentSubProcessDefinition()
            .endEvent("error")
            .moveToActivity(AGENT_ELEMENT_ID)
            .endEvent("end")
            .done();
    final var process = deployProcessAndWaitForIt(client, processModel, "agent_process.bpmn");
    final var processInstance = startProcessInstance(client, process.getBpmnProcessId());
    waitForProcessInstancesToStart(client, 1);

    // Complete the job with a variable to create audit log entries with agentElementId
    final var jobs = waitForJobs(client, List.of(processInstance.getProcessInstanceKey()));
    client
        .newCompleteCommand(jobs.getFirst().getJobKey())
        .variable("testVar", "testValue")
        .send()
        .join();

    // Deploy a process with an external agent service task, whose job type does not carry the
    // legacy agentic job type prefix, and complete its job
    final var externalAgentProcessModel =
        Bpmn.createExecutableProcess("EXTERNAL_AGENT_PROCESS")
            .startEvent()
            .serviceTask(
                EXTERNAL_AGENT_ELEMENT_ID,
                t -> t.zeebeJobType(EXTERNAL_AGENT_JOB_TYPE).zeebeExternalAgentDefinition())
            .endEvent()
            .done();
    final var externalAgentProcess =
        deployProcessAndWaitForIt(client, externalAgentProcessModel, "external_agent_process.bpmn");
    final var externalAgentProcessInstance =
        startProcessInstance(client, externalAgentProcess.getBpmnProcessId());
    waitForProcessInstancesToStart(client, 2);

    final var externalAgentJobs =
        waitForJobs(client, List.of(externalAgentProcessInstance.getProcessInstanceKey()));
    client.newCompleteCommand(externalAgentJobs.getFirst().getJobKey()).send().join();

    // Wait for audit logs to be available
    Awaitility.await("job to be completed")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newAuditLogSearchRequest()
                      .filter(
                          f ->
                              f.operationType(AuditLogOperationTypeEnum.COMPLETE)
                                  .entityType(AuditLogEntityTypeEnum.JOB))
                      .send()
                      .join();

              assertThat(result.items()).hasSize(2);
            });
  }

  @Test
  void shouldAddAgentToVariableAuditLogs(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - search for variable audit logs
    final var result =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.entityType(AuditLogEntityTypeEnum.JOB))
            .send()
            .join();

    // then - audit logs should have the agent element id set
    assertThat(result.items())
        .isNotEmpty()
        .anySatisfy(
            auditLog -> {
              assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
              assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
              assertThat(auditLog.getEntityDescription()).isEqualTo(AGENT_JOB_TYPE);
              assertThat(auditLog.getAgentElementId()).isEqualTo(AGENT_ELEMENT_ID);
            });
  }

  @Test
  void shouldFilterAuditLogsByAgentElementId(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - filter audit logs by the agent element id
    final var result =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.agentElementId(AGENT_ELEMENT_ID))
            .send()
            .join();

    // then - all returned logs should have the matching agent element id
    assertThat(result.items())
        .isNotEmpty()
        .allSatisfy(
            auditLog -> {
              assertThat(auditLog.getAgentElementId()).isEqualTo(AGENT_ELEMENT_ID);
            });
  }

  @Test
  void shouldFilterAuditLogsByAgentElementIdWithAdvancedFilter(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - filter audit logs using advanced filter with like pattern
    final var result =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.agentElementId(p -> p.like("test_agent*")))
            .send()
            .join();

    // then - all returned logs should have the matching agent element id pattern
    assertThat(result.items())
        .isNotEmpty()
        .allSatisfy(
            auditLog -> {
              assertThat(auditLog.getAgentElementId()).startsWith("test_agent");
            });
  }

  @Test
  void shouldAttributeExternalAgentJobInAuditLog(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - search for the external agent job's audit log entry
    final var result =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.entityType(AuditLogEntityTypeEnum.JOB))
            .send()
            .join();

    // then - the entry for the external agent job carries the agent element id
    assertThat(result.items())
        .filteredOn(auditLog -> auditLog.getEntityDescription().equals(EXTERNAL_AGENT_JOB_TYPE))
        .describedAs("audit log entry for the external agent job")
        .hasSize(1)
        .allSatisfy(
            auditLog ->
                assertThat(auditLog.getAgentElementId())
                    .describedAs("agent element id of the external agent job's audit log entry")
                    .isEqualTo(EXTERNAL_AGENT_ELEMENT_ID));

    // when - filter audit logs by the external agent's element id
    final var filteredResult =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.agentElementId(EXTERNAL_AGENT_ELEMENT_ID))
            .send()
            .join();

    // then - the filter finds the external agent's audit log entry
    assertThat(filteredResult.items())
        .describedAs("audit logs filtered by the external agent's element id")
        .isNotEmpty()
        .allSatisfy(
            auditLog ->
                assertThat(auditLog.getAgentElementId()).isEqualTo(EXTERNAL_AGENT_ELEMENT_ID));
  }
}
