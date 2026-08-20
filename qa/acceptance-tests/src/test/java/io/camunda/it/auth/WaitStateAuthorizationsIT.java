/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ElementInstanceWaitStateResult;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Specifies the intended {@code PROCESS_DEFINITION} / {@code READ_PROCESS_INSTANCE} authorization
 * behavior of the element instance wait-state search command.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class WaitStateAuthorizationsIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticatedAccess();

  private static final String PROCESS_ID = "waitStateProcess";
  private static final String OTHER_PROCESS_ID = "otherProcess";
  private static final String PASSWORD = "password";
  private static final List<String> WILDCARD = List.of(AuthorizationScope.WILDCARD.getResourceId());
  private static final String NO_AUTH_USER = "noAuthUser";
  private static final String WILDCARD_USER = "wildcardUser";
  private static final String PROCESS_USER = "processUser";
  private static final String OTHER_PROCESS_USER = "otherProcessUser";

  @UserDefinition
  private static final TestUser NO_AUTH = new TestUser(NO_AUTH_USER, PASSWORD, List.of());

  @UserDefinition
  private static final TestUser WILDCARD_AUTH =
      new TestUser(
          WILDCARD_USER,
          PASSWORD,
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, WILDCARD)));

  @UserDefinition
  private static final TestUser PROCESS_AUTH =
      new TestUser(
          PROCESS_USER,
          PASSWORD,
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(PROCESS_ID))));

  @UserDefinition
  private static final TestUser OTHER_PROCESS_AUTH =
      new TestUser(
          OTHER_PROCESS_USER,
          PASSWORD,
          List.of(
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(OTHER_PROCESS_ID))));

  private static CamundaClient adminClient;

  @BeforeAll
  static void beforeAll() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task")
            .zeebeJobType("svc")
            .endEvent()
            .done();

    deployResource(adminClient, process, "waitStateProcess.bpmn");
    waitForProcessesToBeDeployed(adminClient, 1);

    startProcessInstance(adminClient, PROCESS_ID);
    waitForProcessInstancesToStart(adminClient, 1);

    waitForWaitStates(1);
  }

  @Test
  void shouldDenyUserWithoutAuthorization(@Authenticated(NO_AUTH_USER) final CamundaClient client) {
    // when
    final var result = client.newElementInstanceWaitStateSearchRequest().send().join();

    // then — a user without READ_PROCESS_INSTANCE must not see any wait states
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldAllowUserWithWildcardAuthorization(
      @Authenticated(WILDCARD_USER) final CamundaClient client) {
    // when
    final var result = client.newElementInstanceWaitStateSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementId()).isEqualTo("task");
  }

  @Test
  void shouldAllowUserAuthorizedForTheProcess(
      @Authenticated(PROCESS_USER) final CamundaClient client) {
    // when
    final var result = client.newElementInstanceWaitStateSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementId()).isEqualTo("task");
  }

  @Test
  void shouldDenyUserAuthorizedForOtherProcess(
      @Authenticated(OTHER_PROCESS_USER) final CamundaClient client) {
    // when
    final var result = client.newElementInstanceWaitStateSearchRequest().send().join();

    // then — authorized only for a different process definition, so no results
    assertThat(result.items()).isEmpty();
  }

  private static void waitForWaitStates(final int expectedCount) {
    Awaitility.await("should export %d wait states".formatted(expectedCount))
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () -> {
              final List<ElementInstanceWaitStateResult> items =
                  adminClient.newElementInstanceWaitStateSearchRequest().send().join().items();
              assertThat(items).hasSize(expectedCount);
            });
  }
}
