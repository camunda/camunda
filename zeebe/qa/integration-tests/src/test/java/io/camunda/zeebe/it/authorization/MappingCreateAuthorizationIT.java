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
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
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
public class MappingCreateAuthorizationIT {

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static AuthorizationsUtil authUtil;
  @AutoCloseResource private static ZeebeClient client;

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
    client = createClient(broker, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(broker, client, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
  }

  @Test
  void shouldBeAuthorizedToCreateMappingWithDefaultUser() {
    // when
    final var response =
        client
            .newCreateMappingCommand()
            .claimName("claimName")
            .claimValue("claimValue")
            .send()
            .join();

    // then
    assertThat(response.getMappingKey()).isPositive();
  }

  @Test
  void shouldBeAuthorizedToCreateMappingWithPermissions() {
    // given
    final var authUsername = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        authUsername,
        password,
        new Permissions(ResourceTypeEnum.MAPPING_RULE, PermissionTypeEnum.CREATE, List.of("*")));

    // when
    try (final var client = authUtil.createClient(authUsername, password)) {
      final var response =
          client
              .newCreateMappingCommand()
              .claimName("claimName")
              .claimValue("claimValue")
              .send()
              .join();

      // then
      assertThat(response.getMappingKey()).isPositive();
    }
  }

  @Test
  void shouldBeUnAuthorizedToCreateMappingWithoutPermissions() {
    // given
    final var authUsername = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(authUsername, password);

    // when
    try (final var client = authUtil.createClient(authUsername, password)) {
      final var response =
          client.newCreateMappingCommand().claimName("claimName").claimValue("claimValue").send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: UNAUTHORIZED")
          .hasMessageContaining("status: 401")
          .hasMessageContaining(
              "Unauthorized to perform operation 'CREATE' on resource 'MAPPING_RULE'");
    }
  }
}
