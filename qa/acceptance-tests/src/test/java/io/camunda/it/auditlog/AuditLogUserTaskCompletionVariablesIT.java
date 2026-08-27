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
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeCompleted;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogCategoryEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.api.search.response.AuditLogResult;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class AuditLogUserTaskCompletionVariablesIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withMultiTenancyEnabled()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess()
          .withProperty("camunda.data.audit-log.user-task-completion-variable-audit-enabled", true);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
  private static CamundaClient adminClient;

  @BeforeAll
  static void setup() {
    new AuditLogUtils(adminClient).init();
    RecordingExporter.setMaximumWaitTime(
        CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY.toMillis());
  }

  @Test
  void shouldAuditChangedVariablesAlongsideUserTaskCompletion(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) throws Exception {
    // given
    final String processId = "audit-user-task-completion-variables";
    deploy(processId, userTaskProcess(processId));
    final var processInstance =
        adminClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .tenantId(TENANT_A)
            .variables(Map.of("updated", "before", "unchanged", "same"))
            .send()
            .join();
    final long processInstanceKey = processInstance.getProcessInstanceKey();
    final var userTask =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    // when
    client
        .newCompleteUserTaskCommand(userTask.getUserTaskKey())
        .variables(Map.of("created", "new", "updated", "after", "unchanged", "same"))
        .send()
        .join();
    waitForProcessInstancesToBeCompleted(
        client, filter -> filter.processInstanceKey(processInstanceKey), 1);

    // then
    final var auditLogs = awaitAuditLogs(client, processInstanceKey, 3);
    assertThat(auditLogs)
        .filteredOn(log -> log.getEntityType() == AuditLogEntityTypeEnum.USER_TASK)
        .singleElement()
        .extracting(AuditLogResult::getOperationType)
        .isEqualTo(AuditLogOperationTypeEnum.COMPLETE);

    final var variableAuditLogs =
        auditLogs.stream()
            .filter(log -> log.getEntityType() == AuditLogEntityTypeEnum.VARIABLE)
            .toList();
    assertThat(variableAuditLogs)
        .extracting(AuditLogResult::getEntityDescription, AuditLogResult::getOperationType)
        .containsExactlyInAnyOrder(
            Tuple.tuple("created", AuditLogOperationTypeEnum.CREATE),
            Tuple.tuple("updated", AuditLogOperationTypeEnum.UPDATE));

    final long createdVariableKey =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("created")
            .getFirst()
            .getKey();
    final long updatedVariableKey =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("updated")
            .getFirst()
            .getKey();

    assertThat(variableAuditLogs)
        .allSatisfy(
            auditLog -> {
              assertThat(auditLog.getCategory()).isEqualTo(AuditLogCategoryEnum.USER_TASKS);
              assertThat(auditLog.getResult()).isEqualTo(AuditLogResultEnum.SUCCESS);
              assertThat(auditLog.getActorId()).isEqualTo(DEFAULT_USERNAME);
              assertThat(auditLog.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
              assertThat(auditLog.getProcessInstanceKey())
                  .isEqualTo(String.valueOf(processInstanceKey));
              assertThat(auditLog.getProcessDefinitionKey())
                  .isEqualTo(String.valueOf(processInstance.getProcessDefinitionKey()));
              assertThat(auditLog.getProcessDefinitionId()).isEqualTo(processId);
              assertThat(auditLog.getElementInstanceKey())
                  .isEqualTo(String.valueOf(processInstanceKey));
              assertThat(auditLog.getTenantId()).isEqualTo(TENANT_A);
              assertThat(auditLog.getTimestamp()).isNotNull();
            });
    assertThat(variableAuditLogs)
        .extracting(AuditLogResult::getEntityKey)
        .containsExactlyInAnyOrder(
            String.valueOf(createdVariableKey), String.valueOf(updatedVariableKey));
    assertThat(rawVariableAuditLogs(client, processInstanceKey))
        .allSatisfy(auditLog -> assertThat(auditLog.has("value")).isFalse());
  }

  @Test
  void shouldOnlyAuditOutputMappingResult(
      @Authenticated(DEFAULT_USERNAME) final CamundaClient client) {
    // given
    final String processId = "audit-user-task-output-mapping";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("task", task -> task.zeebeOutputExpression("input", "result"))
            .zeebeUserTask()
            .endEvent()
            .done();
    deploy(processId, process);
    final var processInstance =
        adminClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .tenantId(TENANT_A)
            .send()
            .join();
    final long processInstanceKey = processInstance.getProcessInstanceKey();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    client
        .newCompleteUserTaskCommand(userTaskKey)
        .variables(Map.of("input", "mapped", "discarded", "temporary"))
        .send()
        .join();
    waitForProcessInstancesToBeCompleted(
        client, filter -> filter.processInstanceKey(processInstanceKey), 1);

    // then
    final var auditLogs = awaitAuditLogs(client, processInstanceKey, 2);
    assertThat(auditLogs)
        .filteredOn(log -> log.getEntityType() == AuditLogEntityTypeEnum.VARIABLE)
        .extracting(AuditLogResult::getEntityDescription)
        .containsExactly("result");
  }

  private static void deploy(final String processId, final BpmnModelInstance process) {
    adminClient
        .newDeployResourceCommand()
        .addProcessModel(process, processId + ".bpmn")
        .tenantId(TENANT_A)
        .send()
        .join();
  }

  private static BpmnModelInstance userTaskProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .userTask("task")
        .zeebeUserTask()
        .endEvent()
        .done();
  }

  private static List<AuditLogResult> awaitAuditLogs(
      final CamundaClient client, final long processInstanceKey, final int expectedCount) {
    return Awaitility.await("audit logs are exported")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .until(
            () ->
                client
                    .newAuditLogSearchRequest()
                    .filter(f -> f.processInstanceKey(String.valueOf(processInstanceKey)))
                    .send()
                    .join()
                    .items()
                    .stream()
                    .filter(
                        auditLog ->
                            auditLog.getEntityType() == AuditLogEntityTypeEnum.USER_TASK
                                || auditLog.getEntityType() == AuditLogEntityTypeEnum.VARIABLE)
                    .toList(),
            auditLogs -> auditLogs.size() == expectedCount);
  }

  private static List<JsonNode> rawVariableAuditLogs(
      final CamundaClient client, final long processInstanceKey) throws Exception {
    final String base = client.getConfiguration().getRestAddress().toString();
    final URI uri = URI.create(base + (base.endsWith("/") ? "" : "/") + "v2/audit-logs/search");
    final String credentials =
        Base64.getEncoder()
            .encodeToString((DEFAULT_USERNAME + ":demo").getBytes(StandardCharsets.UTF_8));
    final var request =
        HttpRequest.newBuilder(uri)
            .header("Authorization", "Basic " + credentials)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"filter\":{\"processInstanceKey\":\"%d\"}}".formatted(processInstanceKey)))
            .build();
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var response = httpClient.send(request, BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      return StreamSupport.stream(
              OBJECT_MAPPER.readTree(response.body()).get("items").spliterator(), false)
          .filter(item -> "VARIABLE".equals(item.get("entityType").asText()))
          .toList();
    }
  }
}
