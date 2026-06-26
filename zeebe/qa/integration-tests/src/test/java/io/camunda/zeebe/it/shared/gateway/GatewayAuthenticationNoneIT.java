/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.command.CommandWithCommunicationApiStep;
import io.camunda.client.api.command.TopologyRequestStep1;
import io.camunda.client.api.response.Topology;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.identity.sdk.Identity;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * This test is mostly a copy of {@link GatewayAuthenticationNoneIT} but with the authentication
 * mode set to none. It verifies that the gateway can be configured to not require authentication,
 * even when the {@link Profile#CONSOLIDATED_AUTH} profile is active. In other words, users must be
 * able to override the security configuration with env vars.
 */
@Testcontainers
@ZeebeIntegration
public class GatewayAuthenticationNoneIT {

  public static final String KEYCLOAK_USER = "admin";
  public static final String KEYCLOAK_PASSWORD = "admin";
  // with authentication enabled, the first grpc response includes the warmup of the identity sdk
  public static final Duration FIRST_REQUEST_TIMEOUT = Duration.ofSeconds(5);
  public static final String SNAPSHOT_TAG = "SNAPSHOT";
  private static final String KEYCLOAK_PATH_CAMUNDA_REALM = "/realms/camunda-platform";
  private static final String ORCHESTRATION_CLIENT_ID = "orchestration";
  private static final String ORCHESTRATION_CLIENT_NAME = "Orchestration";
  private static final String ORCHESTRATION_CLIENT_AUDIENCE = "orchestration-api";
  private static final String ORCHESTRATION_CLIENT_SECRET = "zecret";
  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final KeycloakContainer KEYCLOAK =
      DefaultTestContainers.createDefaultKeycloak()
          .withEnv("KEYCLOAK_ADMIN_USER", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK_PASSWORD)
          .withEnv("KEYCLOAK_DATABASE_VENDOR", "dev-mem")
          .withNetwork(NETWORK)
          .withNetworkAliases("keycloak");

  @SuppressWarnings("resource")
  @Container
  private static final GenericContainer<?> IDENTITY =
      new GenericContainer<>(
              DockerImageName.parse("camunda/identity").withTag(getIdentityImageTag()))
          // in case we use SNAPSHOT images they always should get pulled
          .withImagePullPolicy(
              SNAPSHOT_TAG.equals(getIdentityImageTag())
                  ? PullPolicy.alwaysPull()
                  : PullPolicy.defaultPolicy())
          .dependsOn(KEYCLOAK)
          .withEnv("KEYCLOAK_URL", "http://keycloak:8080")
          .withEnv(
              "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
              "http://keycloak:8080" + KEYCLOAK_PATH_CAMUNDA_REALM)
          .withEnv("KEYCLOAK_SETUP_USER", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_SETUP_PASSWORD", KEYCLOAK_PASSWORD)
          .withEnv("KEYCLOAK_INIT_ORCHESTRATION_SECRET", ORCHESTRATION_CLIENT_SECRET)
          .withEnv("KEYCLOAK_INIT_ORCHESTRATION_ROOT_URL", "http://localhost:8080")
          .withEnv("KEYCLOAK_CLIENTS_0_NAME", ORCHESTRATION_CLIENT_NAME)
          .withEnv("KEYCLOAK_CLIENTS_0_ID", ORCHESTRATION_CLIENT_ID)
          .withEnv("KEYCLOAK_CLIENTS_0_SECRET", ORCHESTRATION_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
          .withEnv("IDENTITY_RETRY_ATTEMPTS", "90")
          .withEnv("IDENTITY_RETRY_DELAY_SECONDS", "1")
          // this will enable readiness checks by spring to await ApplicationRunner completion
          .withEnv("MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED", "true")
          .withEnv("MANAGEMENT_HEALTH_READINESSSTATE_ENABLED", "true")
          .withNetwork(NETWORK)
          .withExposedPorts(8080, 8082)
          .waitingFor(
              new HttpWaitStrategy()
                  .forPort(8082)
                  .forPath("/actuator/health/readiness")
                  .allowInsecure()
                  .forStatusCode(200))
          .withNetworkAliases("identity");

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("keycloak", KEYCLOAK, "identity", IDENTITY));

  @TestZeebe(autoStart = false) // must configure in BeforeAll once containers have been started
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withAdditionalProfile(Profile.CONSOLIDATED_AUTH)
          .withProperty("zeebe.broker.gateway.security.authentication.mode", "none")
          .withProperty("camunda.identity.issuerBackendUrl", getKeycloakRealmAddress())
          .withProperty("camunda.identity.audience", ORCHESTRATION_CLIENT_AUDIENCE)
          .withSecondaryStorageType(SecondaryStorageType.elasticsearch);

  @BeforeEach
  void beforeEach() {
    zeebe.start().await(TestHealthProbe.READY);
  }

  @ParameterizedTest
  @MethodSource("provideAPIs")
  void getTopologyRequestSucceedsWithInvalidAuthToken(
      final UnaryOperator<TopologyRequestStep1> apiPicker) {
    // given
    try (final var client =
        createCamundaClientBuilder().credentialsProvider(new InvalidAuthTokenProvider()).build()) {
      // when / then
      sendRequestAndAssertSuccess(apiPicker, client::newTopologyRequest);
    }
  }

  @ParameterizedTest
  @MethodSource("provideAPIs")
  void getTopologyRequestSucceedsWithoutAuthToken(
      final UnaryOperator<TopologyRequestStep1> apiPicker) {
    // given
    try (final var client = createCamundaClientBuilder().build()) {
      // when / then
      sendRequestAndAssertSuccess(apiPicker, client::newTopologyRequest);
    }
  }

  @ParameterizedTest
  @MethodSource("provideAPIs")
  void getTopologyRequestSucceedsWithValidAuthToken(
      final UnaryOperator<TopologyRequestStep1> apiPicker) {
    // given
    try (final var client =
        createCamundaClientBuilder()
            .credentialsProvider(
                CredentialsProvider.newCredentialsProviderBuilder()
                    .clientId(ORCHESTRATION_CLIENT_ID)
                    .clientSecret(ORCHESTRATION_CLIENT_SECRET)
                    .audience(ORCHESTRATION_CLIENT_AUDIENCE)
                    .authorizationServerUrl(
                        getKeycloakRealmAddress() + "/protocol/openid-connect/token")
                    .build())
            .build()) {
      // when / then
      sendRequestAndAssertSuccess(apiPicker, client::newTopologyRequest);
    }
  }

  private static Stream<Named<UnaryOperator<TopologyRequestStep1>>> provideAPIs() {
    return Stream.of(
        Named.of("grpc", CommandWithCommunicationApiStep::useGrpc),
        Named.of("rest", CommandWithCommunicationApiStep::useRest));
  }

  private static void sendRequestAndAssertSuccess(
      final UnaryOperator<TopologyRequestStep1> apiPicker,
      final Supplier<TopologyRequestStep1> requestSupplier) {
    // when
    final Future<Topology> topologyFuture = apiPicker.apply(requestSupplier.get()).send();

    // then
    assertThat(topologyFuture).succeedsWithin(FIRST_REQUEST_TIMEOUT);
    // second request should be faster, given the first request warmed up the token validation
    assertThat(apiPicker.apply(requestSupplier.get()).send().toCompletableFuture())
        .succeedsWithin(Duration.ofSeconds(1));
  }

  private static String getKeycloakRealmAddress() {
    return KEYCLOAK.getAuthServerUrl() + KEYCLOAK_PATH_CAMUNDA_REALM;
  }

  private CamundaClientBuilder createCamundaClientBuilder() {
    return CamundaClient.newClientBuilder()
        .grpcAddress(zeebe.grpcAddress())
        .restAddress(zeebe.restAddress())
        .defaultRequestTimeout(Duration.ofMinutes(1));
  }

  private static String getIdentityImageTag() {
    final String identityVersion = Identity.class.getPackage().getImplementationVersion();
    final String dockerImageTag =
        System.getProperty("identity.docker.image.version", identityVersion);

    return dockerImageTag.contains("SNAPSHOT") ? SNAPSHOT_TAG : dockerImageTag;
  }

  private static final class InvalidAuthTokenProvider implements CredentialsProvider {

    @Override
    public void applyCredentials(final CredentialsApplier applier) {
      applier.put("Authorization", "Bearer youShallNotPass");
    }

    @Override
    public boolean shouldRetryRequest(final StatusCode statusCode) {
      return false;
    }
  }
}
