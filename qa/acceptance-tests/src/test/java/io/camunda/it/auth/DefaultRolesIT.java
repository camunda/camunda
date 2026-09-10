/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
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
  @DisabledIfSystemProperty(
      named = "test.integration.camunda.physical-tenant",
      matches = ".+",
      disabledReason =
          "Secret authorization is resolved against root storage under a physical tenant, so the "
              + "Connectors role grant is not visible in the tenant context; see "
              + "https://github.com/camunda/camunda/issues/58393")
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
