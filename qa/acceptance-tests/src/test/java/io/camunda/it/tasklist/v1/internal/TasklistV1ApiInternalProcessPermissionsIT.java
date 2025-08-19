/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1.internal;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_USER_TASK;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessPublicEndpointsResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class TasklistV1ApiInternalProcessPermissionsIT {
  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

  private static final String ADMIN_USERNAME = "admin";
  private static final String UNAUTHORIZED_USERNAME = "unauthorized";
  @AutoClose private static TestRestTasklistClient authorizedClient;
  @AutoClose private static TestRestTasklistClient unauthorizedClient;

  @UserDefinition
  private static final TestUser ADMIN =
      new TestUser(
          ADMIN_USERNAME,
          ADMIN_USERNAME,
          List.of(
              new Permissions(AUTHORIZATION, CREATE, List.of("*")),
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED =
      new TestUser(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME, List.of());

  @BeforeAll
  public static void beforeAll(@Authenticated(ADMIN_USERNAME) final CamundaClient adminClient)
      throws Exception {
    final var deployedProcess =
        adminClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/process_start_form.bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst();

    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var definition =
                  adminClient
                      .newProcessDefinitionSearchRequest()
                      .filter(
                          f -> f.processDefinitionKey(deployedProcess.getProcessDefinitionKey()))
                      .send()
                      .join();
              assertThat(definition).describedAs("Wait until the definition exists").isNotNull();
            });

    authorizedClient =
        STANDALONE_CAMUNDA
            .newTasklistClient()
            .withAuthentication(ADMIN.username(), ADMIN.password());
    unauthorizedClient =
        STANDALONE_CAMUNDA
            .newTasklistClient()
            .withAuthentication(UNAUTHORIZED.username(), UNAUTHORIZED.password());
  }

  @Test
  void shouldBeAuthorizedToGetPublicEndpoints() throws JsonProcessingException {
    final var response = authorizedClient.getRequest("v1/internal/processes/publicEndpoints");
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    final var publicEndpoints =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(
            response.body(), ProcessPublicEndpointsResponse[].class);
    assertThat(publicEndpoints).hasSize(1);
  }

  @Test
  void shouldBeUnauthorizedToGetPublicEndpoints() throws JsonProcessingException {
    final var response = unauthorizedClient.getRequest("v1/internal/processes/publicEndpoints");
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    final var publicEndpoints =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(response.body(), TaskSearchResponse[].class);
    assertThat(publicEndpoints).isEmpty();
  }
}
