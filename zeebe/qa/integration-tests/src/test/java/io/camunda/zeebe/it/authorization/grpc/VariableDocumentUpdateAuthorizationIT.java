/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization.grpc;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.SetVariablesResponse;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.impl.configuration.AuthenticationCfg.AuthMode;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
public class VariableDocumentUpdateAuthorizationIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static final String PROCESS_ID = "processId";
  private static AuthorizationsUtil authUtil;
  @AutoCloseResource private static ZeebeClient defaultUserClient;

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withGatewayConfig(c -> c.getSecurity().getAuthentication().setMode(AuthMode.IDENTITY))
          .withAdditionalProfile(Profile.AUTH_BASIC);

  @BeforeEach
  void beforeEach() {
    broker.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    broker.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClient(broker, defaultUsername);
    final var clientForPermissions = createClient(broker, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(broker, clientForPermissions, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
    defaultUserClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("task")
                .endEvent()
                .done(),
            "process.xml")
        .send()
        .join();
  }

  @Test
  void shouldBeAuthorizedToUpdateVariablesWithDefaultUser() {
    // given
    final var processInstanceKey = createProcessInstance();

    // when then
    final var response =
        defaultUserClient
            .newSetVariablesCommand(processInstanceKey)
            .variables(Map.of("foo", "bar"))
            .send()
            .join();

    assertThat(response).isInstanceOf(SetVariablesResponse.class);
  }

  @Test
  void shouldBeAuthorizedToUpdateVariablesWithUser() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.UPDATE_PROCESS_INSTANCE,
            List.of(PROCESS_ID)));

    try (final var client = authUtil.createClient(username)) {
      // when then
      final var response =
          client
              .newSetVariablesCommand(processInstanceKey)
              .variables(Map.of("foo", "bar"))
              .send()
              .join();

      assertThat(response).isInstanceOf(SetVariablesResponse.class);
    }
  }

  @Test
  void shouldBeUnauthorizedToUpdateVariablesIfNoPermissions() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    // when
    try (final var client = authUtil.createClient(username)) {
      // when
      final var response =
          client.newSetVariablesCommand(processInstanceKey).variables(Map.of("foo", "bar")).send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ClientStatusException.class)
          .hasMessageContaining("UNAUTHORIZED")
          .hasMessageContaining(
              "Unauthorized to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION' with BPMN process id '%s'",
              PROCESS_ID);
    }
  }

  private long createProcessInstance() {
    return defaultUserClient
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
