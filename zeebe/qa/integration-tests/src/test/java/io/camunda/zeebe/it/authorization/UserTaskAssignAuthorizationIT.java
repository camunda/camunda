/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.awaitUserExistsInElasticsearch;
import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClientWithAuthorization;
import static io.camunda.zeebe.it.util.AuthorizationsUtil.createUserWithPermissions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest.ResourceTypeEnum;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequestPermissionsInner.PermissionTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@AutoCloseResources
@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class UserTaskAssignAuthorizationIT {
  public static final String DEFAULT_USERNAME = "demo";
  public static final String AUTHENTICATED_USERNAME = "foo";
  public static final String UNAUTHENTICATED_USERNAME = "bar";
  public static final String USER_TASK_ID = "userTask";
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

  private static final String PROCESS_ID = "processId";
  @TestZeebe private TestStandaloneBroker zeebe;
  private ZeebeClient defaultUserClient;
  private ZeebeClient authorizedUserClient;
  private ZeebeClient unauthorizedUserClient;

  @BeforeAll
  void beforeAll() throws Exception {
    zeebe =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
            .withBrokerConfig(
                b ->
                    b.getExperimental()
                        .getEngine()
                        .getAuthorizations()
                        .setEnableAuthorization(true))
            .withCamundaExporter("http://" + CONTAINER.getHttpHostAddress())
            .withAdditionalProfile(Profile.AUTH_BASIC);
    zeebe.start();
    defaultUserClient = createClientWithAuthorization(zeebe, DEFAULT_USERNAME, "demo");
    awaitUserExistsInElasticsearch(CONTAINER.getHttpHostAddress(), DEFAULT_USERNAME);
    defaultUserClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask(USER_TASK_ID)
                .zeebeUserTask()
                .endEvent()
                .done(),
            "process.xml")
        .send()
        .join();

    authorizedUserClient =
        createUserWithPermissions(
            zeebe,
            defaultUserClient,
            CONTAINER.getHttpHostAddress(),
            AUTHENTICATED_USERNAME,
            "password",
            new Permissions(
                ResourceTypeEnum.PROCESS_DEFINITION,
                PermissionTypeEnum.UPDATE,
                List.of(PROCESS_ID)));
    unauthorizedUserClient =
        createUserWithPermissions(
            zeebe,
            defaultUserClient,
            CONTAINER.getHttpHostAddress(),
            UNAUTHENTICATED_USERNAME,
            "password");
  }

  @Test
  void shouldBeAuthorizedToAssignUserTaskWithDefaultUser() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey = getUserTaskKey(processInstanceKey);

    // when then
    final var response =
        defaultUserClient
            .newUserTaskAssignCommand(userTaskKey)
            .assignee(DEFAULT_USERNAME)
            .allowOverride(true)
            .send()
            .join();

    // The Rest API returns a null future for an empty response
    // We can verify for null, as if we'd be unauthenticated we'd get an exception
    assertThat(response).isNull();
  }

  @Test
  void shouldBeAuthorizedToAssignUserTaskWithUser() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey = getUserTaskKey(processInstanceKey);

    // when then
    final var response =
        authorizedUserClient
            .newUserTaskAssignCommand(userTaskKey)
            .assignee(AUTHENTICATED_USERNAME)
            .allowOverride(true)
            .send()
            .join();

    // The Rest API returns a null future for an empty response
    // We can verify for null, as if we'd be unauthenticated we'd get an exception
    assertThat(response).isNull();
  }

  @Test
  void shouldBeUnauthorizedToAssignUserTaskIfNoPermissions() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey = getUserTaskKey(processInstanceKey);

    // when we use the unauthorized client
    final var response =
        unauthorizedUserClient
            .newUserTaskAssignCommand(userTaskKey)
            .assignee(UNAUTHENTICATED_USERNAME)
            .allowOverride(true)
            .send();

    // then
    assertThatThrownBy(response::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: UNAUTHORIZED")
        .hasMessageContaining("status: 401")
        .hasMessageContaining(
            "Unauthorized to perform operation 'UPDATE' on resource 'PROCESS_DEFINITION'");
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

  private static long getUserTaskKey(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(USER_TASK_ID)
        .limit(1)
        .findFirst()
        .orElseThrow()
        .getKey();
  }
}
