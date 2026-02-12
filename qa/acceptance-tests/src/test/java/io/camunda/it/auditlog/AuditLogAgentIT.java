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
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.awaitility.Awaitility;
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

  private static CamundaClient adminClient;

  @Test
  void shouldAddAgentToVariableAuditLogs(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var processModel =
        Bpmn.createExecutableProcess("AGENT_PROCESS")
            .startEvent()
            .adHocSubProcess("my_agentic_ahsp", p -> p.task("A1"))
            .zeebeJobType(JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX)
            .endEvent("error")
            .moveToActivity("my_agentic_ahsp")
            .endEvent("end")
            .done();
    final var process = deployProcessAndWaitForIt(client, processModel, "process.bpmn");
    final var processInstance = startProcessInstance(client, process.getBpmnProcessId());
    waitForProcessInstancesToStart(client, 1);

    // when
    final var jobs = waitForJobs(client, List.of(processInstance.getProcessInstanceKey()));
    client.newCompleteCommand(jobs.getFirst().getJobKey()).variable("foo", "bar").send().join();

    // then

    final var auditLogs = new ArrayList<AuditLogResult>();
    Awaitility.await("variable changes are captured in the audit log")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newAuditLogSearchRequest()
                      .filter(f -> f.entityType(AuditLogEntityTypeEnum.VARIABLE))
                      .send()
                      .join();

              // one for the adhoc subprocess variable and one for the job variable
              assertThat(result.items()).hasSize(2);
              auditLogs.addAll((Collection<? extends AuditLogResult>) result.items());
            });

    assertThat(auditLogs.getLast().getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLogs.getLast().getAgentElementId()).isEqualTo("my_agentic_ahsp");
    assertThat(auditLogs.getLast().getEntityDescription()).isEqualTo("foo");
  }
}
