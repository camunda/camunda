/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class WaitStateStatisticsAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String PROCESS_ID = "waitStateStatsAuth";
  private static final String AUTHORIZED = "authorizedUser";
  private static final String UNAUTHORIZED = "unauthorizedUser";

  @UserDefinition
  private static final TestUser AUTHORIZED_USER =
      new TestUser(
          AUTHORIZED,
          "password",
          List.of(
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED, "password", List.of());

  private static CamundaClient camundaClient;

  @Test
  void shouldReturnStatisticsWithReadProcessInstancePermission(
      @Authenticated(AUTHORIZED) final CamundaClient userClient) {
    // given
    final long processInstanceKey = deployAndStartWaitingInstance();

    // when
    final var actual =
        userClient.newProcessInstanceWaitStateStatisticsRequest(processInstanceKey).send().join();

    // then
    assertThat(actual)
        .singleElement()
        .satisfies(s -> assertThat(s.getElementId()).isEqualTo("service-task"));
  }

  @Test
  void shouldRejectWithForbiddenWhenUnauthorized(
      @Authenticated(UNAUTHORIZED) final CamundaClient userClient) {
    // given
    final long processInstanceKey = deployAndStartWaitingInstance();

    // when
    final ThrowingCallable request =
        () ->
            userClient
                .newProcessInstanceWaitStateStatisticsRequest(processInstanceKey)
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(request).actual();
    assertThat(problemException.code()).isEqualTo(403);
  }

  private static long deployAndStartWaitingInstance() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("service-task", t -> t.zeebeJobType("never-activated"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(camundaClient, process, PROCESS_ID + ".bpmn");
    final long processInstanceKey =
        startProcessInstance(camundaClient, PROCESS_ID).getProcessInstanceKey();

    Awaitility.await("wait state visible for instance %d".formatted(processInstanceKey))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .until(
            () ->
                camundaClient
                    .newElementInstanceWaitStateSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey))
                    .send()
                    .join()
                    .items()
                    .size(),
            size -> size == 1);

    return processInstanceKey;
  }
}
