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
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogSearchClientIT {

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
    utils = new AuditLogUtils(adminClient);
    utils.generateDefaults();
    utils.await();
  }

  @Test
  void shouldGetAuditLogByKey(@Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var search = client.newAuditLogSearchRequest().page(p -> p.limit(1)).send().join();

    // when
    final var auditLog = search.items().getFirst();
    final var log = client.newAuditLogGetRequest(auditLog.getAuditLogKey()).send().join();

    // then
    assertThat(log).isNotNull();
    assertThat(log.getAuditLogKey()).isEqualTo(auditLog.getAuditLogKey());
    assertThat(log.getEntityKey()).isEqualTo(auditLog.getEntityKey());
    assertThat(log.getEntityType()).isEqualTo(auditLog.getEntityType());
    assertThat(log.getOperationType()).isEqualTo(auditLog.getOperationType());
    assertThat(log.getCategory()).isEqualTo(auditLog.getCategory());
  }

  @Test
  void shouldSearchAuditLogByProcessInstanceKeyAndOperationType(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final var processInstance = utils.getProcessInstances().getFirst();

    // when
    final var auditLogItems =
        client
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.processInstanceKey(String.valueOf(processInstance.getProcessInstanceKey()))
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.PROCESS_INSTANCE);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.DEPLOYED_RESOURCES);
    assertThat(auditLog.getProcessDefinitionId()).isEqualTo(processInstance.getBpmnProcessId());
    assertThat(auditLog.getProcessDefinitionKey())
        .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
    assertThat(auditLog.getProcessInstanceKey())
        .isEqualTo(String.valueOf(processInstance.getProcessInstanceKey()));
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }

  @Test
  void shouldSearchAuditLogByEntityKeyAndEntityType(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final String tenantId = AuditLogUtils.TENANT_A;

    // when
    // look for tenant creation audit log as the entity key setter is overridden in transformer
    final var auditLogItems =
        client
            .newAuditLogSearchRequest()
            .filter(
                fn ->
                    fn.entityKey(f -> f.in(tenantId))
                        .entityType(f -> f.like("TENANT"))
                        .operationType(AuditLogOperationTypeEnum.CREATE))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLogItems.items().size()).isEqualTo(1);
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.TENANT);
    assertThat(auditLog.getOperationType()).isEqualTo(AuditLogOperationTypeEnum.CREATE);
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
  }
}
