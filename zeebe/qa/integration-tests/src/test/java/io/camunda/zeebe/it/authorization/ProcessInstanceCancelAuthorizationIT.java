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
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
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
public class ProcessInstanceCancelAuthorizationIT {
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
    defaultUserClient = createClientWithAuthorization(zeebe, "demo", "demo");
    awaitUserExistsInElasticsearch(CONTAINER.getHttpHostAddress(), "demo");
    defaultUserClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done(),
            "process.xml")
        .send()
        .join();

    authorizedUserClient =
        createUserWithPermissions(
            zeebe,
            defaultUserClient,
            CONTAINER.getHttpHostAddress(),
            "foo",
            "password",
            new Permissions(
                ResourceTypeEnum.PROCESS_DEFINITION,
                PermissionTypeEnum.UPDATE,
                List.of(PROCESS_ID)));
    unauthorizedUserClient =
        createUserWithPermissions(
            zeebe, defaultUserClient, CONTAINER.getHttpHostAddress(), "bar", "password");
  }

  @Test
  void shouldBeAuthorizedToCancelInstanceWithDefaultUser() {
    // given
    final var processInstanceEvent =
        defaultUserClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // when
    final var response =
        defaultUserClient
            .newCancelInstanceCommand(processInstanceEvent.getProcessInstanceKey())
            .send()
            .join();

    // then
    assertThat(response).isNull();
  }

  @Test
  void shouldBeAuthorizedToCreateInstanceWithUser() {
    // given
    final var processInstanceEvent =
        defaultUserClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // when we use the authorizedUserClient
    final var response =
        authorizedUserClient
            .newCancelInstanceCommand(processInstanceEvent.getProcessInstanceKey())
            .send()
            .join();

    // then
    assertThat(response).isNull();
  }

  @Test
  void shouldBeUnauthorizedToCreateInstanceIfNoPermissions() {
    // given
    final var processInstanceEvent =
        defaultUserClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // when we use the unauthorizedUserClient
    final var response =
        unauthorizedUserClient
            .newCancelInstanceCommand(processInstanceEvent.getProcessInstanceKey())
            .send();

    // then
    assertThatThrownBy(response::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: UNAUTHORIZED")
        .hasMessageContaining("status: 401")
        .hasMessageContaining(
            "Unauthorized to perform operation 'UPDATE' on resource 'PROCESS_DEFINITION'");
  }
}
