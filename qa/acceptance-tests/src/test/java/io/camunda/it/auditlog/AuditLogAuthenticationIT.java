/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_A;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_A_ID;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/*
 * Test is disabled on RDBMS until https://github.com/camunda/camunda/issues/43323 is implemented.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogAuthenticationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static CamundaClient adminClient;

  @BeforeAll
  static void setup() {
    new AuditLogUtils(adminClient, true).init();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetActorAndTenantBasedOnAuthenticatedUser(
      final boolean useRestApi, @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    final var utils = new AuditLogUtils(client, useRestApi);
    final var deployment = utils.deployResource("a.bpmn", PROCESS_A, TENANT_A);
    final var instance = utils.startProcessInstance(PROCESS_A_ID, TENANT_A);
    utils.await();

    final var auditLogs =
        client
            .newAuditLogSearchRequest()
            .filter(f -> f.processInstanceKey("" + instance.getProcessInstanceKey()))
            .send()
            .join();

    assertThat(auditLogs.items())
        .hasSize(1)
        .allSatisfy(
            log -> {
              assertThat(log.getProcessInstanceKey())
                  .isEqualTo("" + instance.getProcessInstanceKey());
              assertThat(log.getActorId()).isEqualTo(DEFAULT_USERNAME);
              assertThat(log.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
              assertThat(log.getTenantId()).isEqualTo(TENANT_A);
            });
  }
}
