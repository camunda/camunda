/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE_TASK_LISTENER;
import static io.camunda.client.api.search.enums.PermissionType.DELETE_TASK_LISTENER;
import static io.camunda.client.api.search.enums.PermissionType.READ_TASK_LISTENER;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_TASK_LISTENER;
import static io.camunda.client.api.search.enums.ResourceType.GLOBAL_LISTENER;
import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD_CHAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class GlobalTaskListenerAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String CREATE_AUTHORIZED = "createAuthorized";
  private static final String READ_AUTHORIZED = "readAuthorized";
  private static final String UPDATE_AUTHORIZED = "updateAuthorized";
  private static final String DELETE_AUTHORIZED = "deleteAuthorized";
  private static final String UNAUTHORIZED = "unauthorized";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(GLOBAL_LISTENER, CREATE_TASK_LISTENER, List.of(WILDCARD_CHAR)),
              new Permissions(GLOBAL_LISTENER, READ_TASK_LISTENER, List.of(WILDCARD_CHAR)),
              new Permissions(GLOBAL_LISTENER, UPDATE_TASK_LISTENER, List.of(WILDCARD_CHAR)),
              new Permissions(GLOBAL_LISTENER, DELETE_TASK_LISTENER, List.of(WILDCARD_CHAR))));

  @UserDefinition
  private static final TestUser CREATE_AUTHORIZED_USER =
      new TestUser(
          CREATE_AUTHORIZED,
          "password",
          List.of(new Permissions(GLOBAL_LISTENER, CREATE_TASK_LISTENER, List.of(WILDCARD_CHAR))));

  @UserDefinition
  private static final TestUser READ_AUTHORIZED_USER =
      new TestUser(
          READ_AUTHORIZED,
          "password",
          List.of(new Permissions(GLOBAL_LISTENER, READ_TASK_LISTENER, List.of(WILDCARD_CHAR))));

  @UserDefinition
  private static final TestUser UPDATE_AUTHORIZED_USER =
      new TestUser(
          UPDATE_AUTHORIZED,
          "password",
          List.of(new Permissions(GLOBAL_LISTENER, UPDATE_TASK_LISTENER, List.of(WILDCARD_CHAR))));

  @UserDefinition
  private static final TestUser DELETE_AUTHORIZED_USER =
      new TestUser(
          DELETE_AUTHORIZED,
          "password",
          List.of(new Permissions(GLOBAL_LISTENER, DELETE_TASK_LISTENER, List.of(WILDCARD_CHAR))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED, "password", List.of());

  @Test
  void shouldAllowCreateOfGlobalTaskListenerWithCreateAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(CREATE_AUTHORIZED) final CamundaClient authorizedClient) {
    // given a global listener id
    final var listenerId = UUID.randomUUID().toString();

    // when an authorized user tries to create the global listener
    authorizedClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();

    // then the global listener is correctly created
    Awaitility.await("global listener should become available in search layer")
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(() -> adminClient.newGlobalTaskListenerGetRequest(listenerId).send().join() != null);
  }

  @Test
  void shouldAllowReadOfGlobalTaskListenerWithReadAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(READ_AUTHORIZED) final CamundaClient authorizedClient) {
    // given an existing global listener
    final var listenerId = UUID.randomUUID().toString();
    adminClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();
    waitForCreatedGlobalListener(adminClient, listenerId);

    // when an authorized user tries to read the global listener
    final var readListener =
        authorizedClient.newGlobalTaskListenerGetRequest(listenerId).send().join();

    // then the listener is correctly retrieved
    assertThat(readListener).as("global listener should be retrieved").isNotNull();
  }

  @Test
  void shouldAllowUpdateOfGlobalTaskListenerWithUpdateAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(UPDATE_AUTHORIZED) final CamundaClient authorizedClient) {
    // given an existing global listener
    final var listenerId = UUID.randomUUID().toString();
    adminClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();
    waitForCreatedGlobalListener(adminClient, listenerId);

    // when an authorized user tries to update the global listener
    authorizedClient
        .newUpdateGlobalTaskListenerRequest(listenerId)
        .type("updated-job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();

    // then the global listener is correctly updated
    Awaitility.await("global listener should have data updated in search layer")
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () -> {
              final var globalListener =
                  adminClient.newGlobalTaskListenerGetRequest(listenerId).send().join();
              return globalListener != null && globalListener.getType().equals("updated-job-type");
            });
  }

  @Test
  void shouldAllowDeleteOfGlobalTaskListenerWithDeleteAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(DELETE_AUTHORIZED) final CamundaClient authorizedClient) {
    // given an existing global listener
    final var listenerId = UUID.randomUUID().toString();
    adminClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();
    waitForCreatedGlobalListener(adminClient, listenerId);

    // when an authorized user tries to delete the global listener
    authorizedClient.newDeleteGlobalTaskListenerRequest(listenerId).send().join();

    // then the global listener is correctly deleted
    Awaitility.await("global listener should become unavailable in search layer")
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () ->
                assertThatThrownBy(
                        () -> adminClient.newGlobalTaskListenerGetRequest(listenerId).send().join())
                    .isInstanceOf(io.camunda.client.api.command.ProblemException.class)
                    .satisfies(
                        e -> {
                          final var problemException =
                              (io.camunda.client.api.command.ProblemException) e;
                          assertThat(problemException.code()).isEqualTo(404);
                        }));
  }

  @Test
  void shouldAllowSearchOfGlobalTaskListenerWithSearchAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(READ_AUTHORIZED) final CamundaClient authorizedClient) {
    // given an existing global listener
    final var listenerId = UUID.randomUUID().toString();
    adminClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();
    waitForCreatedGlobalListener(adminClient, listenerId);

    // when an authorized user tries to search the global listener
    final var foundListeners =
        authorizedClient
            .newGlobalTaskListenerSearchRequest()
            .filter(f -> f.id(listenerId))
            .send()
            .join();

    // then the global listener is correctly updated
    assertThat(foundListeners).as("search result should not be null").isNotNull();
    assertThat(foundListeners.items())
        .as("the desired global listener should be found in search result")
        .hasSize(1);
  }

  @Test
  void shouldForbidCreateOfGlobalTaskListenerWithoutAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(UNAUTHORIZED) final CamundaClient unauthorizedClient) {
    // given a global listener id
    final var listenerId = UUID.randomUUID().toString();

    // when an unauthorized user tries to create the global listener
    // then a 403 Forbidden error is returned
    assertThatThrownBy(
            () ->
                unauthorizedClient
                    .newCreateGlobalTaskListenerRequest()
                    .id(listenerId)
                    .type("job-type")
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .isInstanceOf(io.camunda.client.api.command.ProblemException.class)
        .satisfies(
            e -> {
              final var problemException = (io.camunda.client.api.command.ProblemException) e;
              assertThat(problemException.code()).isEqualTo(403);
            });
  }

  @Test
  void shouldForbidReadOfGlobalTaskListenerWithoutAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(UNAUTHORIZED) final CamundaClient unauthorizedClient) {
    // given an existing global listener
    final var listenerId = UUID.randomUUID().toString();
    adminClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();
    waitForCreatedGlobalListener(adminClient, listenerId);

    // when an unauthorized user tries to read the global listener
    // then a 403 Forbidden error is returned
    assertThatThrownBy(
            () -> unauthorizedClient.newGlobalTaskListenerGetRequest(listenerId).send().join())
        .isInstanceOf(io.camunda.client.api.command.ProblemException.class)
        .satisfies(
            e -> {
              final var problemException = (io.camunda.client.api.command.ProblemException) e;
              assertThat(problemException.code()).isEqualTo(403);
            });
  }

  @Test
  void shouldForbidUpdateOfGlobalTaskListenerWithoutAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(UNAUTHORIZED) final CamundaClient unauthorizedClient) {
    // given an existing global listener
    final var listenerId = UUID.randomUUID().toString();
    adminClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();
    waitForCreatedGlobalListener(adminClient, listenerId);

    // when an unauthorized user tries to update the global listener
    // then a 403 Forbidden error is returned
    assertThatThrownBy(
            () ->
                unauthorizedClient
                    .newUpdateGlobalTaskListenerRequest(listenerId)
                    .type("updated-job-type")
                    .eventTypes(GlobalTaskListenerEventType.CREATING)
                    .send()
                    .join())
        .isInstanceOf(io.camunda.client.api.command.ProblemException.class)
        .satisfies(
            e -> {
              final var problemException = (io.camunda.client.api.command.ProblemException) e;
              assertThat(problemException.code()).isEqualTo(403);
            });
  }

  @Test
  void shouldForbidDeleteOfGlobalTaskListenerWithoutAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(UNAUTHORIZED) final CamundaClient unauthorizedClient) {
    // given an existing global listener
    final var listenerId = UUID.randomUUID().toString();
    adminClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();
    waitForCreatedGlobalListener(adminClient, listenerId);

    // when an unauthorized user tries to delete the global listener
    // then a 403 Forbidden error is returned
    assertThatThrownBy(
            () -> unauthorizedClient.newDeleteGlobalTaskListenerRequest(listenerId).send().join())
        .isInstanceOf(io.camunda.client.api.command.ProblemException.class)
        .satisfies(
            e -> {
              final var problemException = (io.camunda.client.api.command.ProblemException) e;
              assertThat(problemException.code()).isEqualTo(403);
            });
  }

  @Test
  void shouldForbidSearchOfGlobalTaskListenerWithoutAuthorization(
      @Authenticated(ADMIN) final CamundaClient adminClient,
      @Authenticated(UNAUTHORIZED) final CamundaClient unauthorizedClient) {
    // given an existing global listener
    final var listenerId = UUID.randomUUID().toString();
    adminClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("job-type")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();
    waitForCreatedGlobalListener(adminClient, listenerId);

    // when an unauthorized user tries to read the global listener
    // then a 403 Forbidden error is returned
    assertThatThrownBy(
            () -> unauthorizedClient.newGlobalTaskListenerGetRequest(listenerId).send().join())
        .isInstanceOf(io.camunda.client.api.command.ProblemException.class)
        .satisfies(
            e -> {
              final var problemException = (io.camunda.client.api.command.ProblemException) e;
              assertThat(problemException.code()).isEqualTo(403);
            });
  }

  public static void waitForCreatedGlobalListener(
      final CamundaClient camundaClient, final String globalListenerId) {
    Awaitility.await("should wait for global listener to be available in search layer")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .until(
            () ->
                camundaClient.newGlobalTaskListenerGetRequest(globalListenerId).send().join()
                    != null);
  }
}
