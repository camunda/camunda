/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.zeebe.containers.ZeebeContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class GatewayAuthenticationIdentityIT {

  public static final String KEYCLOAK_USER = "admin";
  public static final String KEYCLOAK_PASSWORD = "admin";
  public static final BpmnModelInstance PROCESS_MODEL =
      Bpmn.createExecutableProcess("model").startEvent().endEvent().done();
  private static final String KEYCLOAK_PATH_CAMUNDA_REALM = "/realms/camunda-platform";
  private static final String ZEEBE_CLIENT_ID = "zeebe";
  private static final String ZEEBE_CLIENT_AUDIENCE = "zeebe-api";
  private static final String ZEEBE_CLIENT_SECRET = "zecret";
  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final GenericContainer KEYCLOAK =
      new GenericContainer<>("quay.io/keycloak/keycloak:20.0.5")
          .withEnv("KC_HEALTH_ENABLED", "true")
          .withEnv("KEYCLOAK_ADMIN", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK_PASSWORD)
          .withNetwork(NETWORK)
          .withNetworkAliases("keycloak")
          .withExposedPorts(8080)
          .withCommand("start-dev")
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8080)
                  .forPath("/health/ready")
                  .allowInsecure()
                  .forStatusCode(200));

  @Container
  private static final GenericContainer<?> IDENTITY =
      new GenericContainer<>(
              DockerImageName.parse("camunda/identity").withTag(getIdentityImageTag()))
          .dependsOn(KEYCLOAK)
          .withEnv("KEYCLOAK_URL", "http://keycloak:8080")
          .withEnv(
              "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
              "http://keycloak:8080" + KEYCLOAK_PATH_CAMUNDA_REALM)
          .withEnv("KEYCLOAK_SETUP_USER", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_SETUP_PASSWORD", KEYCLOAK_PASSWORD)
          .withEnv("KEYCLOAK_INIT_ZEEBE_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_NAME", ZEEBE_CLIENT_ID)
          .withEnv("KEYCLOAK_CLIENTS_0_ID", ZEEBE_CLIENT_ID)
          .withEnv("KEYCLOAK_CLIENTS_0_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("IDENTITY_RETRY_ATTEMPTS", "90")
          .withEnv("IDENTITY_RETRY_DELAY_SECONDS", "1")
          .withNetwork(NETWORK)
          .withExposedPorts(8080, 8082)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8082)
                  .forPath("/actuator/health")
                  .allowInsecure()
                  .forStatusCode(200))
          .withNetworkAliases("identity");

  @Container
  private static final ZeebeContainer ZEEBE =
      new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
          .withNetwork(NETWORK)
          .withoutTopologyCheck()
          .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_MODE", "identity")
          .withEnv(
              "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_ISSUERBACKENDURL",
              "http://keycloak:8080" + KEYCLOAK_PATH_CAMUNDA_REALM)
          .withEnv(
              "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_AUDIENCE",
              ZEEBE_CLIENT_AUDIENCE);

  @Test
  void deployModelFailsWithoutAuthToken() {
    // given
    try (final var client = createZeebeClientBuilder().build()) {
      // when
      final var deploymentFuture =
          client
              .newDeployResourceCommand()
              .addProcessModel(PROCESS_MODEL, "model.bpmn")
              .send()
              .toCompletableFuture();

      // then
      assertThat(deploymentFuture)
          .failsWithin(Duration.ofSeconds(1))
          .withThrowableOfType(ExecutionException.class)
          .withCauseInstanceOf(StatusRuntimeException.class)
          .extracting(
              Throwable::getCause, as(InstanceOfAssertFactories.type(StatusRuntimeException.class)))
          .satisfies(
              statusRuntimeException -> {
                assertThat(statusRuntimeException.getStatus())
                    .hasFieldOrPropertyWithValue("code", Status.UNAUTHENTICATED.getCode())
                    .hasFieldOrPropertyWithValue(
                        "description",
                        "Expected bearer token at header with key [authorization], but found nothing");
              });
    }
  }

  @Test
  void deployModelFailsWithInvalidAuthToken() {
    // given
    try (final var client =
        createZeebeClientBuilder().credentialsProvider(new InvalidAuthTokenProvider()).build()) {
      // when
      final var deploymentFuture =
          client
              .newDeployResourceCommand()
              .addProcessModel(PROCESS_MODEL, "model.bpmn")
              .send()
              .toCompletableFuture();

      // then
      assertThat(deploymentFuture)
          .failsWithin(Duration.ofSeconds(1))
          .withThrowableOfType(ExecutionException.class)
          .withCauseInstanceOf(StatusRuntimeException.class)
          .extracting(
              Throwable::getCause, as(InstanceOfAssertFactories.type(StatusRuntimeException.class)))
          .satisfies(
              statusRuntimeException -> {
                assertThat(statusRuntimeException.getStatus())
                    .hasFieldOrPropertyWithValue("code", Status.UNAUTHENTICATED.getCode())
                    .hasFieldOrPropertyWithValue(
                        "description", "Failed to parse bearer token, see cause for details");
              });
    }
  }

  @Test
  void deployModelSucceedsWithValidAuthToken() {
    // given
    awaitCamundaRealmAvailabilityOnKeycloak();

    try (final var client =
        createZeebeClientBuilder()
            .credentialsProvider(
                new OAuthCredentialsProviderBuilder()
                    .clientId(ZEEBE_CLIENT_ID)
                    .clientSecret(ZEEBE_CLIENT_SECRET)
                    .audience(ZEEBE_CLIENT_AUDIENCE)
                    .authorizationServerUrl(
                        getKeycloakRealmAddress() + "/protocol/openid-connect/token")
                    .build())
            .build()) {
      // when
      final var deploymentFuture =
          client
              .newDeployResourceCommand()
              .addProcessModel(PROCESS_MODEL, "model.bpmn")
              .send()
              .toCompletableFuture();

      // then
      assertThat(deploymentFuture).succeedsWithin(Duration.ofSeconds(1));
    }
  }

  /**
   * Awaits the presence of the Camunda realm on the keycloak container. Once Keycloak and Identity
   * booted up, Identity will eventually configure the Camunda Realm on Keycloak.
   */
  private static void awaitCamundaRealmAvailabilityOnKeycloak() {
    final var httpClient = HttpClient.newHttpClient();
    final HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create(getKeycloakRealmAddress())).build();
    Awaitility.await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final HttpResponse<String> response =
                  httpClient.send(request, BodyHandlers.ofString());
              assertThat(response.statusCode()).isEqualTo(200);
            });
  }

  private static String getKeycloakRealmAddress() {
    return "http://localhost:" + KEYCLOAK.getFirstMappedPort() + KEYCLOAK_PATH_CAMUNDA_REALM;
  }

  private ZeebeClientBuilder createZeebeClientBuilder() {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(ZEEBE.getExternalGatewayAddress())
        .defaultRequestTimeout(Duration.ofMinutes(1))
        .usePlaintext();
  }

  private static String getIdentityImageTag() {
    return System.getProperty("identity.docker.image.version", "SNAPSHOT");
  }

  private static class InvalidAuthTokenProvider implements CredentialsProvider {

    @Override
    public void applyCredentials(final Metadata headers) {
      headers.put(
          Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer youShallNotPass");
    }

    @Override
    public boolean shouldRetryRequest(final Throwable throwable) {
      return false;
    }
  }
}
