/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
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

  private static CamundaClient adminClient;
  private static AuditLogUtils utils;

  @BeforeAll
  static void setup() {
    utils = new AuditLogUtils(adminClient).init();
  }

  @Test
  void shouldSearchUserTaskAuditLogWithSort(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var processModel =
        Bpmn.createExecutableProcess("AGENT_PROCESS")
            .startEvent()
            .adHocSubProcess("my_ahsp", p -> p.task("A1"))
            .zeebeJobType("my job")
            .endEvent("error")
            .moveToActivity("my_ahsp")
            .endEvent("end")
            .done();

    utils.deployResource("ahsp.bpmn", processModel, utils.TENANT_A);
    utils.startProcessInstance("AGENT_PROCESS", utils.TENANT_A);
    utils.await();

    final var results = client.newJobSearchRequest().filter(f -> f.type("my job")).send().join();

    final var completion =
        client
            .newCompleteCommand(results.items().getFirst().getJobKey())
            .variable("foo", "bar")
            .send()
            .join();

    // when
    final var auditLogItems =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.entityType(AuditLogEntityTypeEnum.VARIABLE))
            .send()
            .join();

    assertThat(auditLogItems.items()).isNotEmpty();
    assertThat(auditLogItems.items().getLast().getActorType())
        .isEqualTo(AuditLogActorTypeEnum.AGENT);
  }
}
