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
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest.ResourceTypeEnum;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequestPermissionsInner.PermissionTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
public class UserCreateAuthorizationIT {
  private static final DockerImageName ELASTIC_IMAGE =
      DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
          .withTag(RestClient.class.getPackage().getImplementationVersion());

  @Container
  private static final ElasticsearchContainer CONTAINER =
      new ElasticsearchContainer(ELASTIC_IMAGE)
          // use JVM option files to avoid overwriting default options set by the ES container class
          .withClasspathResourceMapping(
              "elasticsearch-fast-startup.options",
              "/usr/share/elasticsearch/config/jvm.options.d/ elasticsearch-fast-startup.options",
              BindMode.READ_ONLY)
          // can be slow in CI
          .withStartupTimeout(Duration.ofMinutes(5))
          .withEnv("action.auto_create_index", "true")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("xpack.watcher.enabled", "false")
          .withEnv("xpack.ml.enabled", "false")
          .withEnv("action.destructive_requires_name", "false");

  @TestZeebe(autoStart = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withBrokerConfig(
              b -> b.getExperimental().getEngine().getAuthorizations().setEnableAuthorization(true))
          .withAdditionalProfile(Profile.AUTH_BASIC);

  private static AuthorizationsUtil authUtil;
  private static ZeebeClient defaultUserClient;

  @BeforeAll
  static void beforeAll() {
    BROKER.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    BROKER.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClient(BROKER, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(BROKER, defaultUserClient, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
  }

  @Test
  void shouldBeAuthorizedToCreateUserWithDefaultUser() {
    // given
    final var username = UUID.randomUUID().toString();

    // when
    final var response =
        defaultUserClient
            .newUserCreateCommand()
            .username(username)
            .name("Foo")
            .email("bar@baz.com")
            .password("zabraboof")
            .send()
            .join();

    // then
    assertThat(response.getUserKey()).isPositive();
  }

  @Test
  void shouldBeAuthorizedToCreateUserWithPermissions() {
    // given
    final var authUsername = UUID.randomUUID().toString();
    final var newUsername = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        authUsername,
        password,
        new Permissions(ResourceTypeEnum.USER, PermissionTypeEnum.CREATE, List.of("*")));

    try (final var client = authUtil.createClient(authUsername, password)) {
      // when
      final var response =
          client
              .newUserCreateCommand()
              .username(newUsername)
              .name("Foo")
              .email("bar@baz.com")
              .password("zabraboof")
              .send()
              .join();

      // then
      assertThat(response.getUserKey()).isPositive();
    }
  }

  @Test
  void shouldBeUnAuthorizedToCreateUserWithoutPermissions() {
    // given
    final var authUsername = UUID.randomUUID().toString();
    final var newUsername = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(authUsername, password);

    // when
    try (final var client = authUtil.createClient(authUsername, password)) {
      final var response =
          client
              .newUserCreateCommand()
              .username(newUsername)
              .name("Foo")
              .email("bar@baz.com")
              .password("zabraboof")
              .send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: UNAUTHORIZED")
          .hasMessageContaining("status: 401")
          .hasMessageContaining("Unauthorized to perform operation 'CREATE' on resource 'USER'");
    }
  }
}
