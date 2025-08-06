/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.BATCH;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasVariable;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
// Operate API does not support RDBMS
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class OperateProcessInstanceAddVariableIT {
  @MultiDbTestApplication
  static final TestCamundaApplication CAMUNDA_APPLICATION =
      new TestCamundaApplication()
          // Enable the StartupBean which enables the operation executor
          .withAdditionalProfile("test-executor")
          .withAuthorizationsEnabled()
          .withBasicAuth();

  private static final String SUPER_USER_USERNAME = "super";

  @UserDefinition
  private static final TestUser SUPER_USER =
      new TestUser(
          SUPER_USER_USERNAME,
          "password",
          List.of(
              new Permissions(BATCH, READ, List.of("*")),
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, UPDATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  private static TestRestOperateClient operateClient;

  @BeforeAll
  public static void beforeAll() {
    operateClient =
        CAMUNDA_APPLICATION.newOperateClient(SUPER_USER.username(), SUPER_USER.password());
  }

  @Test
  void shouldAddVariableProcess(
      @Authenticated(SUPER_USER_USERNAME) final CamundaClient client, final TestInfo testInfo) {
    final var testScopeId =
        testInfo.getTestMethod().map(Method::toString).orElse(UUID.randomUUID().toString());
    // given
    // process instances that are running
    deployProcessAndWaitForIt(client, "process/service_tasks_v1.bpmn");

    final List<Long> processInstanceKeys =
        createProcessInstances(client, "service_tasks_v1", 1, testScopeId);

    // when
    // execute ADD_VARIABLE
    final var processInstanceKey = processInstanceKeys.getFirst();
    final var response =
        operateClient.addVariable(
            processInstanceKey, String.valueOf(processInstanceKey), "foo", "\"bar\"");
    EitherAssert.assertThat(response).isRight();
    final var batchOperationId = response.get();
    assertThat(batchOperationId).isNotNull();

    // then
    // This should:
    //
    //   * Create a batch operation
    //   * Create an operation for the ADD_VARIABLE operation
    //   * Trigger the variable addition via Command on Zeebe
    //   * Zeebe should process and export the respective events
    //   * The process should have the new variable

    waitForBatchOperationWithCorrectTotalCount(client, batchOperationId, 1);
    waitUntilProcessInstanceHasVariable(client, processInstanceKey, "foo", "\"bar\"");
    waitForBatchOperationCompleted(client, batchOperationId, 1, 0);
  }

  private List<Long> createProcessInstances(
      final CamundaClient client,
      final String bpmnProcessId,
      final int amount,
      final String testScopeId) {

    final var activeProcessInstances =
        IntStream.range(0, amount)
            .mapToObj(i -> startScopedProcessInstance(client, bpmnProcessId, testScopeId, Map.of()))
            .toList();

    waitForScopedProcessInstancesToStart(client, testScopeId, activeProcessInstances.size());

    return activeProcessInstances.stream()
        .map(ProcessInstanceEvent::getProcessInstanceKey)
        .collect(Collectors.toList());
  }
}
