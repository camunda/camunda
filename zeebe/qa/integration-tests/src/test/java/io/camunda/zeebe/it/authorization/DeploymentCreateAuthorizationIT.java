/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.Profile;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest.ResourceTypeEnum;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequestPermissionsInner.PermissionTypeEnum;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
final class DeploymentCreateAuthorizationIT {
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

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  @TestZeebe private TestStandaloneBroker zeebe;
  private ZeebeClient defaultUserClient;
  private ZeebeClient client;

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
    defaultUserClient = createClient("demo", "demo");
  }

  @BeforeEach
  void beforeEach() throws Exception {
    awaitUserExistsInElasticsearch("demo");
  }

  @Test
  void shouldBeAuthorizedToDeployWithDefaultUser() throws Exception {
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
  void shouldBeAuthorizedToDeployWithPermissions() throws Exception {
    // given
    final var username = "foo";
    final var password = "password";
    final var processId = Strings.newRandomValidBpmnId();
    final var userResponse =
        defaultUserClient
            .newUserCreateCommand()
            .username(username)
            .password(password)
            .name("name")
            .email("foo@bar.com")
            .send()
            .join();
    defaultUserClient
        .newAddPermissionsCommand(userResponse.getUserKey())
        .resourceType(ResourceTypeEnum.DEPLOYMENT)
        .permission(PermissionTypeEnum.CREATE)
        .resourceId("*")
        .send()
        .join();
    initClient(username, password);
    awaitUserExistsInElasticsearch(username);

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

  @Test
  void shouldBeUnAuthorizedToDeployWithPermissions() throws Exception {
    // given
    final var username = "bar";
    final var password = "password";
    final var processId = Strings.newRandomValidBpmnId();
    defaultUserClient
        .newUserCreateCommand()
        .username(username)
        .password(password)
        .name("name")
        .email("foo@bar.com")
        .send()
        .join();
    initClient(username, password);
    awaitUserExistsInElasticsearch(username);

    // when
    final var deployFuture =
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                "process.bpmn")
            .send();

    // then
    assertThatThrownBy(deployFuture::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: UNAUTHORIZED")
        .hasMessageContaining("status: 401")
        .hasMessageContaining(
            "Unauthorized to perform operation 'CREATE' on resource 'DEPLOYMENT'");
  }

  private void initClient(final String username, final String password) {
    client = createClient(username, password);
  }

  private ZeebeClient createClient(final String username, final String password) {
    return zeebe
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .defaultRequestTimeout(Duration.ofSeconds(15))
        .credentialsProvider(
            new CredentialsProvider() {
              @Override
              public void applyCredentials(final CredentialsApplier applier) {
                applier.put(
                    "Authorization",
                    "Basic %s"
                        .formatted(
                            Base64.getEncoder()
                                .encodeToString("%s:%s".formatted(username, password).getBytes())));
              }

              @Override
              public boolean shouldRetryRequest(final StatusCode statusCode) {
                return false;
              }
            })
        .build();
  }

  private void awaitUserExistsInElasticsearch(final String username) throws Exception {
    final var request =
        HttpRequest.newBuilder()
            .POST(
                BodyPublishers.ofString(
                    """
                    {
                      "query": {
                        "match": {
                          "username": "%s"
                        }
                      }
                    }"""
                        .formatted(username)))
            .uri(new URI("http://%s/users/_count/".formatted(CONTAINER.getHttpHostAddress())))
            .header("Content-Type", "application/json")
            .build();

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () -> {
              final var response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
              final var userExistsResponse =
                  OBJECT_MAPPER.readValue(response.body(), UserExistsResponse.class);
              return userExistsResponse.count > 0;
            });
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record UserExistsResponse(int count) {}
}
