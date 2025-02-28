/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.identity.sdk.Identity;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.command.CommandWithCommunicationApiStep;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.command.TopologyRequestStep1;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.apache.hc.core5.http.HttpStatus;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.ThrowableAssertAlternative;
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

@Testcontainers
@ZeebeIntegration
public abstract class GatewayAuthenticationIdentityAbstractIT<T extends TestGateway<T>> {

  public static final String KEYCLOAK_USER = "admin";
  public static final String KEYCLOAK_PASSWORD = "admin";
  // with authentication enabled, the first grpc response includes the warmup of the identity sdk
  public static final Duration FIRST_REQUEST_TIMEOUT = Duration.ofSeconds(5);
  public static final String SNAPSHOT_TAG = "SNAPSHOT";
  protected static final String ZEEBE_CLIENT_AUDIENCE = "zeebe-api";
  private static final String KEYCLOAK_PATH_CAMUNDA_REALM = "/realms/camunda-platform";
  private static final String ZEEBE_CLIENT_ID = "zeebe";
  private static final String ZEEBE_CLIENT_SECRET = "zecret";
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
          .withEnv("KEYCLOAK_INIT_ZEEBE_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_NAME", ZEEBE_CLIENT_ID)
          .withEnv("KEYCLOAK_CLIENTS_0_ID", ZEEBE_CLIENT_ID)
          .withEnv("KEYCLOAK_CLIENTS_0_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write:*")
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

  @BeforeEach
  void beforeEach() {
    getZeebe().start();
  }

  @ParameterizedTest
  @MethodSource("provideInvalidTokenCases")
  void getTopologyRequestFailsWithInvalidAuthToken(final InvalidTokenTestCase testCase) {
    // given
    try (final var client =
        createZeebeClientBuilder().credentialsProvider(new InvalidAuthTokenProvider()).build()) {
      // when
      final Future<?> topologyFuture = testCase.apiPicker.apply(client.newTopologyRequest()).send();

      // then
      final var assertion =
          assertThat(topologyFuture)
              .failsWithin(FIRST_REQUEST_TIMEOUT)
              .withThrowableOfType(ExecutionException.class);
      testCase.expectations.accept(assertion);
    }
  }

  @ParameterizedTest
  @MethodSource("provideInvalidTokenCases")
  void getTopologyRequestFailsWithoutAuthToken(final InvalidTokenTestCase testCase) {
    // given
    try (final var client = createZeebeClientBuilder().build()) {
      // when
      final Future<?> topologyFuture = testCase.apiPicker.apply(client.newTopologyRequest()).send();

      // then
      final var assertion =
          assertThat(topologyFuture)
              .failsWithin(FIRST_REQUEST_TIMEOUT)
              .withThrowableOfType(ExecutionException.class);
      testCase.expectations.accept(assertion);
    }
  }

  @ParameterizedTest
  @MethodSource("provideValidTokenCases")
  void getTopologyRequestSucceedsWithValidAuthToken(
      final UnaryOperator<TopologyRequestStep1> apiPicker) {
    // given
    try (final var client =
        createZeebeClientBuilder()
            .credentialsProvider(
                CredentialsProvider.newCredentialsProviderBuilder()
                    .clientId(ZEEBE_CLIENT_ID)
                    .clientSecret(ZEEBE_CLIENT_SECRET)
                    .audience(ZEEBE_CLIENT_AUDIENCE)
                    .authorizationServerUrl(
                        getKeycloakRealmAddress() + "/protocol/openid-connect/token")
                    .build())
            .build()) {

      // when
      final Future<io.camunda.zeebe.client.api.response.Topology> topologyFuture =
          apiPicker.apply(client.newTopologyRequest()).send();

      // then
      assertThat(topologyFuture).succeedsWithin(FIRST_REQUEST_TIMEOUT);
      // second request should be faster, given the first request warmed up the token validation
      assertThat(client.newTopologyRequest().send().toCompletableFuture())
          .succeedsWithin(Duration.ofSeconds(1));
    }
  }

  private static Stream<Named<UnaryOperator<TopologyRequestStep1>>> provideValidTokenCases() {
    return Stream.of(
        Named.of("grpc", CommandWithCommunicationApiStep::useGrpc),
        Named.of("rest", CommandWithCommunicationApiStep::useRest));
  }

  private static Stream<Named<InvalidTokenTestCase>> provideInvalidTokenCases() {
    return Stream.of(
        Named.of(
            "grpc",
            new InvalidTokenTestCase(
                CommandWithCommunicationApiStep::useGrpc,
                assertion ->
                    assertion
                        .havingCause()
                        .asInstanceOf(InstanceOfAssertFactories.type(StatusRuntimeException.class))
                        .extracting(StatusRuntimeException::getStatus)
                        .returns(Code.UNAUTHENTICATED, Status::getCode))),
        Named.of(
            "rest",
            new InvalidTokenTestCase(
                CommandWithCommunicationApiStep::useRest,
                assertion ->
                    assertion
                        .havingCause()
                        .asInstanceOf(InstanceOfAssertFactories.type(ProblemException.class))
                        .returns(HttpStatus.SC_UNAUTHORIZED, ProblemException::code))));
  }

  protected static String getKeycloakRealmAddress() {
    return "http://"
        + KEYCLOAK.getHost()
        + ":"
        + KEYCLOAK.getFirstMappedPort()
        + KEYCLOAK_PATH_CAMUNDA_REALM;
  }

  private ZeebeClientBuilder createZeebeClientBuilder() {
    return ZeebeClient.newClientBuilder()
        .grpcAddress(getZeebe().grpcAddress())
        .restAddress(getZeebe().restAddress())
        .defaultRequestTimeout(Duration.ofMinutes(1))
        .usePlaintext();
  }

  private static String getIdentityImageTag() {
    final String identityVersion = Identity.class.getPackage().getImplementationVersion();
    final String dockerImageTag =
        System.getProperty("identity.docker.image.version", identityVersion);

    return dockerImageTag.contains("SNAPSHOT") ? SNAPSHOT_TAG : dockerImageTag;
  }

  protected abstract TestGateway<T> getZeebe();

  protected record InvalidTokenTestCase(
      UnaryOperator<TopologyRequestStep1> apiPicker,
      Consumer<ThrowableAssertAlternative<?>> expectations) {}

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
