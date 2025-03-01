/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClientGrpc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class BasicAuthOverGrpcIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static AuthorizationsUtil authUtil;
  @AutoClose private static CamundaClient defaultUserClient;

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  @BeforeEach
  void beforeEach() {
    broker.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    broker.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClientGrpc(broker, defaultUsername, "demo");
    final var clientForPermissions = createClient(broker, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(broker, clientForPermissions, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
  }

  @Test
  void shouldBeAuthorizedWithDefaultUser() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when then
    final var deploymentEvent =
        defaultUserClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                "process.bpmn")
            .send()
            .join();
    assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);
  }

  @Test
  void shouldBeAuthorizedWithUserThatIsGrantedPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var username = Strings.newRandomValidUsername();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(ResourceTypeEnum.RESOURCE, PermissionTypeEnum.CREATE, List.of("*")));

    try (final var client = authUtil.createClientGrpc(username, password)) {
      // when
      final var deploymentEvent =
          client
              .newDeployResourceCommand()
              .addProcessModel(
                  Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                  "process.bpmn")
              .send()
              .join();

      // then
      assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId()).isEqualTo(processId);
    }
  }

  @Test
  void shouldBeUnauthorizedWithUserThatIsNotGrantedPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var username = Strings.newRandomValidUsername();
    final var password = "password";
    authUtil.createUser(username, password);

    // when
    try (final var client = authUtil.createClientGrpc(username, password)) {
      final var deployFuture =
          client
              .newDeployResourceCommand()
              .addProcessModel(
                  Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                  "process.bpmn")
              .send();

      // then
      assertThatThrownBy(deployFuture::join)
          .isInstanceOf(ClientStatusException.class)
          .hasMessageContaining("FORBIDDEN")
          .hasMessageContaining(
              "Insufficient permissions to perform operation 'CREATE' on resource 'RESOURCE'");
    }
  }
}
