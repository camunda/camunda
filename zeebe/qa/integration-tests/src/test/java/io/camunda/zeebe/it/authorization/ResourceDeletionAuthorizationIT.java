/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
public class ResourceDeletionAuthorizationIT {

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static AuthorizationsUtil authUtil;
  @AutoCloseResource private static CamundaClient defaultUserClient;

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withAdditionalProfile(Profile.AUTH_BASIC);

  @BeforeEach
  void beforeEach() {
    broker.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    broker.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClient(broker, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(broker, defaultUserClient, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
  }

  @Test
  void shouldBeAuthorizedToDeleteProcessDefinitionWithDefaultUser() {
    // given
    final var processDefinitionKey = deployProcessDefinition(Strings.newRandomValidBpmnId());

    // when
    final var response =
        defaultUserClient.newDeleteResourceCommand(processDefinitionKey).send().join();

    // then
    assertThat(response).isNull();
  }

  @Test
  void shouldBeAuthorizedToDeleteProcessDefinitionWithPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var processDefinitionKey = deployProcessDefinition(processId);
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.DEPLOYMENT, PermissionTypeEnum.DELETE_PROCESS, List.of(processId)));

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response = client.newDeleteResourceCommand(processDefinitionKey).send().join();

      // then
      assertThat(response).isNull();
    }
  }

  @Test
  void shouldBeUnAuthorizedToDeleteProcessDefinitionWithPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var processDefinitionKey = deployProcessDefinition(processId);
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response = client.newDeleteResourceCommand(processDefinitionKey).send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: FORBIDDEN")
          .hasMessageContaining("status: 403")
          .hasMessageContaining(
              "Insufficient permissions to perform operation 'DELETE_PROCESS' on resource 'DEPLOYMENT', required resource identifiers are one of '[*, %s]",
              processId);
    }
  }

  @Test
  void shouldBeAuthorizedToDeleteDrdWithDefaultUser() {
    // given
    final var drdKey = deployDrd();

    // when
    final var response = defaultUserClient.newDeleteResourceCommand(drdKey).send().join();

    // then
    assertThat(response).isNull();
  }

  @Test
  void shouldBeAuthorizedToDeleteDrdWithPermissions() {
    // given
    final var drdId = "force_users";
    final var drdKey = deployDrd();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.DEPLOYMENT,
            io.camunda.client.protocol.rest.PermissionTypeEnum.DELETE_DRD,
            List.of(drdId)));

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response = client.newDeleteResourceCommand(drdKey).send().join();

      // then
      assertThat(response).isNull();
    }
  }

  @Test
  void shouldBeUnAuthorizedToDeleteDrdWithPermissions() {
    // given
    final var drdId = "force_users";
    final var drdKey = deployDrd();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response = client.newDeleteResourceCommand(drdKey).send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: FORBIDDEN")
          .hasMessageContaining("status: 403")
          .hasMessageContaining(
              "Insufficient permissions to perform operation 'DELETE_DRD' on resource 'DEPLOYMENT', required resource identifiers are one of '[*, %s]'",
              drdId);
    }
  }

  @Test
  void shouldBeAuthorizedToDeleteFormWithDefaultUser() {
    // given
    final var formKey = deployForm();

    // when
    final var response = defaultUserClient.newDeleteResourceCommand(formKey).send().join();

    // then
    assertThat(response).isNull();
  }

  @Test
  void shouldBeAuthorizedToDeleteFormWithPermissions() {
    // given
    final var formId = "Form_0w7r08e";
    final var formKey = deployForm();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.DEPLOYMENT, PermissionTypeEnum.DELETE_FORM, List.of(formId)));

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response = client.newDeleteResourceCommand(formKey).send().join();

      // then
      assertThat(response).isNull();
    }
  }

  @Test
  void shouldBeUnAuthorizedToDeleteFormWithPermissions() {
    // given
    final var formId = "Form_0w7r08e";
    final var formKey = deployForm();
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response = client.newDeleteResourceCommand(formKey).send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: FORBIDDEN")
          .hasMessageContaining("status: 403")
          .hasMessageContaining(
              "Insufficient permissions to perform operation 'DELETE_FORM' on resource 'DEPLOYMENT', required resource identifiers are one of '[*, %s]",
              formId);
    }
  }

  private static long deployProcessDefinition(final String processId) {
    return defaultUserClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(), "process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static long deployDrd() {
    return defaultUserClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("dmn/drg-force-user.dmn")
        .send()
        .join()
        .getDecisionRequirements()
        .getFirst()
        .getDecisionRequirementsKey();
  }

  private static long deployForm() {
    return defaultUserClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("form/test-form-1.form")
        .send()
        .join()
        .getForm()
        .getFirst()
        .getFormKey();
  }
}
