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
public class AuthorizationRemovePermissionAuthorizationIT {
  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static final String PROCESS_ID = "processId";
  @AutoCloseResource private static CamundaClient defaultUserClient;
  private static AuthorizationsUtil authUtil;

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
    defaultUserClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done(), "process.xml")
        .send()
        .join();
  }

  @Test
  void shouldBeAuthorizedToRemovePermissionsWithDefaultUser() {
    // given
    final var username = UUID.randomUUID().toString();
    final var resourceType = ResourceTypeEnum.DEPLOYMENT;
    final var permissionType = PermissionTypeEnum.DELETE;
    final var resourceId = "resourceId";
    final var userKey =
        authUtil.createUserWithPermissions(
            username,
            "password",
            new Permissions(resourceType, permissionType, List.of(resourceId)));

    // when then
    final var response =
        defaultUserClient
            .newRemovePermissionsCommand(userKey)
            .resourceType(resourceType)
            .permission(permissionType)
            .resourceId(resourceId)
            .send()
            .join();

    // The Rest API returns a null future for an empty response
    // We can verify for null, as if we'd be unauthenticated we'd get an exception
    assertThat(response).isNull();
  }

  @Test
  void shouldBeAuthorizedToRemovePermissionsWithUser() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    final var resourceType = ResourceTypeEnum.AUTHORIZATION;
    final var permissionType = PermissionTypeEnum.UPDATE;
    final var resourceId = "*";
    final var userKey =
        authUtil.createUserWithPermissions(
            username, password, new Permissions(resourceType, permissionType, List.of(resourceId)));

    try (final var client = authUtil.createClient(username, password)) {
      // when then
      final var response =
          client
              .newRemovePermissionsCommand(userKey)
              .resourceType(resourceType)
              .permission(permissionType)
              .resourceId(resourceId)
              .send()
              .join();
      // The Rest API returns a null future for an empty response
      // We can verify for null, as if we'd be unauthenticated we'd get an exception
      assertThat(response).isNull();
    }
  }

  @Test
  void shouldBeUnauthorizedToRemovePermissionsIfNoPermissions() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    // when
    try (final var client = authUtil.createClient(username, password)) {
      final var response =
          client
              // We can use any owner key. The authorization check happens before we use it.
              .newRemovePermissionsCommand(1L)
              .resourceType(ResourceTypeEnum.DEPLOYMENT)
              .permission(PermissionTypeEnum.CREATE)
              .resourceId("*")
              .send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: FORBIDDEN")
          .hasMessageContaining("status: 403")
          .hasMessageContaining(
              "Insufficient permissions to perform operation 'UPDATE' on resource 'AUTHORIZATION'");
    }
  }
}
