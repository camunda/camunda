/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.RoleUser;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Future;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
final class DefaultRolesIT {

  private static final String TOKEN_VALUE = "token-file-value";
  private static final String KNOWN_REFERENCE = "camunda.secrets.token";

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withFileBasedSecretStore(
              directory -> {
                Files.writeString(directory.resolve("token"), TOKEN_VALUE, StandardCharsets.UTF_8);
              });

  private static final String DEFAULT_PASSWORD = "password";
  private static final String CONNECTORS_USERNAME = "connectors";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          "admin",
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(ResourceType.ROLE, PermissionType.READ, List.of("*")),
              new Permissions(ResourceType.ROLE, PermissionType.UPDATE, List.of("*")),
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*"))));

  @Authenticated("admin")
  private static CamundaClient adminClient;

  @UserDefinition
  private static final TestUser CONNECTORS_USER =
      new TestUser(CONNECTORS_USERNAME, DEFAULT_PASSWORD, List.of());

  @BeforeAll
  static void setUp() {
    adminClient
        .newAssignRoleToUserCommand()
        .roleId(DefaultRole.CONNECTORS.getId())
        .username(CONNECTORS_USERNAME)
        .send()
        .join();
    awaitConnectorsRoleMembershipVisible();
  }

  /**
   * Waits until the membership assigned above is readable from secondary storage, which the
   * command's own completion does not imply: it is acknowledged once the engine has processed it,
   * while the exporter writes and indexes the record afterwards.
   *
   * <p>{@link #shouldResolveSecrets} needs that, and {@link #shouldCreateProcessInstances} does
   * not, because the two are authorized in different places. Creating a process instance is
   * authorized inside the engine, against engine state the command has already reached by the time
   * it is acknowledged. Resolving a secret is authorized in the gateway by {@code SecretServices},
   * which asks an {@code AuthorizationChecker} backed by the authorization index — so an unindexed
   * membership reads as no grant at all, and the reference comes back {@code ACCESS_DENIED} rather
   * than resolved. That surfaces as a plain unresolved response, not an error, so without this wait
   * the race fails the assertion instead of the request.
   */
  private static void awaitConnectorsRoleMembershipVisible() {
    Awaitility.await("until the connectors role membership is visible in secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newUsersByRoleSearchRequest(DefaultRole.CONNECTORS.getId())
                            .send()
                            .join()
                            .items())
                    .extracting(RoleUser::getUsername)
                    .contains(CONNECTORS_USERNAME));
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/38751")
  void shouldCreateProcessInstances(
      @Authenticated(CONNECTORS_USERNAME) final CamundaClient client) {
    // given
    final var definition =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("process").startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();

    // when
    final var result =
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(definition.getProcesses().getFirst().getProcessDefinitionKey())
            .send();

    // then
    assertThat((Future<?>) result).succeedsWithin(Duration.ofSeconds(30));
  }

  @RegressionTest("https://github.com/camunda/connectors/issues/8222")
  void shouldResolveSecrets(@Authenticated(CONNECTORS_USERNAME) final CamundaClient client) {
    // when
    final var response =
        client.newResolveSecretsCommand().references(List.of(KNOWN_REFERENCE)).send().join();

    // then the connectors role's default SECRET:REVEAL grant lets it resolve without any
    // additional authorization being configured
    assertThat(response.isFullyResolved()).isTrue();
    assertThat(response.getResolved()).hasSize(1);
    assertThat(response.getResolved().get(0).getReference()).isEqualTo(KNOWN_REFERENCE);
    assertThat(response.getResolved().get(0).getValue()).isEqualTo(TOKEN_VALUE);
  }
}
