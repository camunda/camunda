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
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogAgentIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String AGENT_ELEMENT_ID = "test_agent_ahsp";
  private static CamundaClient adminClient;

  @BeforeAll
  static void setup(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // Deploy a process with an agent ad-hoc subprocess and complete a job with variables
    final var processModel =
        Bpmn.createExecutableProcess("AGENT_PROCESS")
            .startEvent()
            .adHocSubProcess(AGENT_ELEMENT_ID, p -> p.task("A1"))
            .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
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

    // Wait for audit logs to be available
    Awaitility.await("process instance to be completed")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              Thread.sleep(3000);
              final var result =
                  client
                      .newAuditLogSearchRequest()
                      .filter(
                          f ->
                              f.operationType(AuditLogOperationTypeEnum.CREATE)
                                  .entityType(AuditLogEntityTypeEnum.VARIABLE))
                      .send()
                      .join();

              // first is the internal adhoc subprocess variable, second is the job variable
              assertThat(result.items()).hasSizeGreaterThan(1);
            });
  }

  @Test
  void shouldAddAgentToVariableAuditLogs(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // when - search for variable audit logs
    final var result =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.entityType(AuditLogEntityTypeEnum.VARIABLE))
            .send()
            .join();

    // then - audit logs should have the agent element id set
    assertThat(result.items().getLast())
        .isNotNull()
        .satisfies(
            auditLog -> {
              assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
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
}
