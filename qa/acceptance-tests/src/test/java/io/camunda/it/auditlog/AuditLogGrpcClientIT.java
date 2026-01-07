/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.auditlog.AuditLogUtils.TENANT_A;
import static io.camunda.it.auditlog.AuditLogUtils.generateData;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.util.collection.Tuple;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/*
 * Test is disabled on RDBMS until https://github.com/camunda/camunda/issues/43323 is implemented.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogGrpcClientIT {

  protected static final boolean USE_REST_API = false;

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static CamundaClient adminClient;
  private static ProcessInstanceEvent processInstanceEvent;
  private static String auditLogKey;

  @BeforeAll
  static void setup() {
    final Tuple<String, ProcessInstanceEvent> tuple = generateData(adminClient, USE_REST_API);
    auditLogKey = tuple.getLeft();
    processInstanceEvent = tuple.getRight();
  }

  @Test
  void shouldSearchAuditLogEntryFromPICreation(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient adminClient) {
    // given
    final var processInstanceKey = processInstanceEvent.getProcessInstanceKey();

    // when
    final var auditLogItems =
        adminClient
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.processInstanceKey(String.valueOf(processInstanceKey))
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().get(0);
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.PROCESS_INSTANCE);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.DEPLOYED_RESOURCES);
    assertThat(auditLog.getProcessDefinitionId())
        .isEqualTo(processInstanceEvent.getBpmnProcessId());
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstanceEvent.getProcessDefinitionKey()));
    assertThat(auditLog.getProcessInstanceKey())
        .isEqualTo(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
    assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }

  @Test
  void shouldFetchAuditLogEntryByAuditLogKey(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient adminClient) {
    // when
    final var fetchedAuditLog = adminClient.newAuditLogGetRequest(auditLogKey).send().join();

    // then
    // verify the fetched audit log entry matches the searched one
    assertThat(fetchedAuditLog).isNotNull();
    assertThat(fetchedAuditLog.getAuditLogKey()).isEqualTo(auditLogKey);
    assertThat(fetchedAuditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.PROCESS_INSTANCE);
    assertThat(fetchedAuditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(fetchedAuditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.DEPLOYED_RESOURCES);
    assertThat(fetchedAuditLog.getProcessDefinitionId())
        .isEqualTo(processInstanceEvent.getBpmnProcessId());
    assertThat(fetchedAuditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstanceEvent.getProcessDefinitionKey()));
    assertThat(fetchedAuditLog.getProcessInstanceKey())
        .isEqualTo(String.valueOf(processInstanceEvent.getProcessInstanceKey()));
    assertThat(fetchedAuditLog.getTenantId()).isEqualTo(TENANT_A);
    assertThat(fetchedAuditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }
}
