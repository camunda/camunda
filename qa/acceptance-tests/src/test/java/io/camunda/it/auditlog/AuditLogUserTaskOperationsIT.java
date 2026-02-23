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
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogUserTaskOperationsIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static UserTaskRecordValue userTask;
  private static UserTaskRecordValue unassignedUserTask;
  private static CamundaClient adminClient;
  private static AuditLogUtils utils;

  @BeforeAll
  static void setup() {
    utils = new AuditLogUtils(adminClient).generateDefaults();
    userTask = utils.getUserTasks().getFirst();
    updateUserTask(userTask);
    completeUserTask(userTask);
    unassignedUserTask = utils.getUserTasks().get(1);
    unassignUserTask(unassignedUserTask);
  }

  @Test
  void shouldSearchUserTaskAuditLogWithSort(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final long userTaskKey = userTask.getUserTaskKey();

    // when
    final var auditLogItems =
        client
            .newUserTaskAuditLogSearchRequest(userTaskKey)
            .sort(s -> s.timestamp().desc())
            .send()
            .join();

    // then
    assertThat(auditLogItems.items().size()).isEqualTo(3);
    assertThat(auditLogItems.items().get(0).getOperationType())
        .isEqualTo(AuditLogOperationTypeEnum.COMPLETE);
    assertThat(auditLogItems.items().get(1).getOperationType())
        .isEqualTo(AuditLogOperationTypeEnum.UPDATE);
    assertThat(auditLogItems.items().get(2).getOperationType())
        .isEqualTo(AuditLogOperationTypeEnum.ASSIGN);
    for (int i = 0; i < auditLogItems.items().size() - 1; i++) {
      final var current = auditLogItems.items().get(i);
      final var next = auditLogItems.items().get(i + 1);
      assertThat(current.getTimestamp()).isGreaterThanOrEqualTo(next.getTimestamp());
    }
  }

  @Test
  void shouldSearchUserTaskAuditLogByKeyWithAssignOperation(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final long userTaskKey = userTask.getUserTaskKey();

    // when
    final var auditLogItems =
        client
            .newUserTaskAuditLogSearchRequest(userTaskKey)
            .filter(f -> f.operationType(AuditLogOperationTypeEnum.ASSIGN))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getUserTaskKey()).isEqualTo(String.valueOf(userTaskKey));
    assertThat(auditLog.getRelatedEntityKey()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getRelatedEntityType()).isEqualTo(AuditLogEntityTypeEnum.USER);
    assertUserTaskAuditLog(
        auditLog, AuditLogOperationTypeEnum.ASSIGN, userTask.getProcessDefinitionKey());
  }

  @Test
  void shouldSearchUserTaskAuditLogByKeyWithUnassignOperation(
      @Authenticated final CamundaClient client) {
    // given
    final long userTaskKey = unassignedUserTask.getUserTaskKey();

    // when
    final var auditLogItems =
        client
            .newUserTaskAuditLogSearchRequest(userTaskKey)
            .filter(f -> f.operationType(AuditLogOperationTypeEnum.UNASSIGN))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getUserTaskKey()).isEqualTo(String.valueOf(userTaskKey));
    assertThat(auditLog.getRelatedEntityKey()).isBlank();
    assertThat(auditLog.getRelatedEntityType()).isEqualTo(AuditLogEntityTypeEnum.USER);
    assertUserTaskAuditLog(
        auditLog, AuditLogOperationTypeEnum.UNASSIGN, unassignedUserTask.getProcessDefinitionKey());
  }

  @Test
  void shouldSearchUserTaskAuditLogByKeyWithUpdateOperation(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final long userTaskKey = userTask.getUserTaskKey();

    // when
    final var auditLogItems =
        client
            .newUserTaskAuditLogSearchRequest(userTaskKey)
            .filter(f -> f.operationType(AuditLogOperationTypeEnum.UPDATE))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getUserTaskKey()).isEqualTo(String.valueOf(userTaskKey));
    assertUserTaskAuditLog(
        auditLog, AuditLogOperationTypeEnum.UPDATE, userTask.getProcessDefinitionKey());
  }

  @Test
  void shouldSearchUserTaskAuditLogByKeyWithCompleteOperation(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final long userTaskKey = userTask.getUserTaskKey();

    // when
    final var auditLogItems =
        client
            .newUserTaskAuditLogSearchRequest(userTaskKey)
            .filter(f -> f.operationType(AuditLogOperationTypeEnum.COMPLETE))
            .send()
            .join();

    // then
    final var auditLog = auditLogItems.items().getFirst();
    assertThat(auditLog).isNotNull();
    assertThat(auditLog.getUserTaskKey()).isEqualTo(String.valueOf(userTaskKey));
    assertUserTaskAuditLog(
        auditLog, AuditLogOperationTypeEnum.COMPLETE, userTask.getProcessDefinitionKey());
  }

  // ========================================================================================
  // Helper Methods
  // ========================================================================================

  /**
   * Asserts common audit log fields for user task operations.
   *
   * @param auditLog the audit log to verify
   * @param operationType the expected operation type
   * @param processDefinitionKey the expected process definition key
   */
  private void assertUserTaskAuditLog(
      final io.camunda.client.api.search.response.AuditLogResult auditLog,
      final AuditLogOperationTypeEnum operationType,
      final long processDefinitionKey) {
    assertThat(auditLog.getEntityType()).isEqualTo(AuditLogEntityTypeEnum.USER_TASK);
    assertThat(auditLog.getOperationType()).isEqualTo(operationType);
    assertThat(auditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.USER_TASKS);
    assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
    assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
    assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
    assertThat(auditLog.getProcessDefinitionKey()).isEqualTo(String.valueOf(processDefinitionKey));
  }

  public static void unassignUserTask(final UserTaskRecordValue userTask) {
    adminClient.newUnassignUserTaskCommand(userTask.getUserTaskKey()).send().join();

    Awaitility.await("Audit log entry is created for the updated user task")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var auditLogItems =
                  adminClient
                      .newAuditLogSearchRequest()
                      .filter(
                          fn ->
                              fn.processInstanceKey(
                                      String.valueOf(userTask.getProcessInstanceKey()))
                                  .entityType(AuditLogEntityTypeEnum.USER_TASK)
                                  .operationType(AuditLogOperationTypeEnum.UNASSIGN))
                      .send()
                      .join();
              assertThat(auditLogItems.items()).hasSize(1);
            });
  }

  public static void updateUserTask(final UserTaskRecordValue userTask) {
    adminClient.newUpdateUserTaskCommand(userTask.getUserTaskKey()).priority(99).send().join();

    Awaitility.await("Audit log entry is created for the updated user task")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var auditLogItems =
                  adminClient
                      .newAuditLogSearchRequest()
                      .filter(
                          fn ->
                              fn.processInstanceKey(
                                      String.valueOf(userTask.getProcessInstanceKey()))
                                  .entityType(AuditLogEntityTypeEnum.USER_TASK)
                                  .operationType(AuditLogOperationTypeEnum.UPDATE))
                      .send()
                      .join();
              assertThat(auditLogItems.items()).hasSize(1);
            });
  }

  public static void completeUserTask(final UserTaskRecordValue userTask) {
    adminClient.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();

    Awaitility.await("Audit log entry is created for the completed user task")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              final var auditLogItems =
                  adminClient
                      .newAuditLogSearchRequest()
                      .filter(
                          fn ->
                              fn.processInstanceKey(
                                      String.valueOf(userTask.getProcessInstanceKey()))
                                  .entityType(AuditLogEntityTypeEnum.USER_TASK)
                                  .operationType(AuditLogOperationTypeEnum.COMPLETE))
                      .send()
                      .join();
              assertThat(auditLogItems.items()).hasSize(1);
            });
  }
}
