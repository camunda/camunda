/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.application.Profile;
import io.camunda.identity.sdk.Identity;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.gateway.impl.configuration.AuthenticationCfg.AuthMode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Verifies that data can be isolated per tenant using Identity as the tenant provider. */
@Testcontainers
@AutoCloseResources
public class MultiTenancyOverIdentityHighRequestCountIT {

  private static final String IDENTITY_SNAPSHOT_TAG = "SNAPSHOT";
  @TempDir private static Path credentialsCacheDir;
  private static final String DEFAULT_TENANT = TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  private static final String DATABASE_HOST = "postgres";
  private static final int DATABASE_PORT = 5432;
  private static final String DATABASE_USER = "postgres";
  private static final String DATABASE_PASSWORD = "zecret";
  private static final String DATABASE_NAME = "postgres";

  private static final String KEYCLOAK_USER = "admin";
  private static final String KEYCLOAK_PASSWORD = "admin";
  private static final String KEYCLOAK_PATH_CAMUNDA_REALM = "/realms/camunda-platform";
  private static final String ZEEBE_CLIENT_ID_TENANT_A = "zeebe-tenant-a-and-default";
  private static final String ZEEBE_CLIENT_ID_TENANT_B = "zeebe-tenant-b-and-default";
  private static final String ZEEBE_CLIENT_ID_TENANT_A_AND_B = "zeebe-tenant-a-and-b-and-default";
  private static final String ZEEBE_CLIENT_ID_TENANT_DEFAULT = ZEEBE_CLIENT_ID_TENANT_A;
  private static final String ZEEBE_CLIENT_ID_WITHOUT_TENANT = "zeebe-without-tenant";
  private static final String ZEEBE_CLIENT_AUDIENCE = "zeebe-api";
  private static final String ZEEBE_CLIENT_SECRET = "zecret";

  private static final Network NETWORK = Network.newNetwork();

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15.0-alpine")
          .withExposedPorts(DATABASE_PORT)
          .withDatabaseName(DATABASE_NAME)
          .withUsername(DATABASE_USER)
          .withPassword(DATABASE_PASSWORD)
          .withNetwork(NETWORK)
          .withNetworkAliases(DATABASE_HOST);

  @Container
  private static final KeycloakContainer KEYCLOAK =
      DefaultTestContainers.createDefaultKeycloak()
          .withEnv("KEYCLOAK_ADMIN_USER", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", KEYCLOAK_PASSWORD)
          .withEnv("KEYCLOAK_DATABASE_VENDOR", "dev-mem")
          .withNetwork(NETWORK)
          .withNetworkAliases("keycloak")
          .withExposedPorts(8080);

  @Container
  @SuppressWarnings("resource")
  private static final GenericContainer<?> IDENTITY =
      new GenericContainer<>(
              DockerImageName.parse("camunda/identity").withTag(getIdentityImageTag()))
          .withImagePullPolicy(
              System.getProperty("identity.docker.image.version", "SNAPSHOT").equals("SNAPSHOT")
                  ? PullPolicy.alwaysPull()
                  : PullPolicy.defaultPolicy())
          .dependsOn(POSTGRES, KEYCLOAK)
          .withEnv("MULTITENANCY_ENABLED", "true")
          .withEnv("RESOURCE_AUTHORIZATIONS_ENABLED", "true")
          .withEnv("IDENTITY_LOG_LEVEL", "TRACE")
          .withEnv("logging_level_org_springframework_security", "DEBUG")
          .withEnv("LOGGING_LEVEL_org.springframework", "DEBUG")
          .withEnv("KEYCLOAK_URL", "http://keycloak:8080")
          .withEnv(
              "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
              "http://keycloak:8080" + KEYCLOAK_PATH_CAMUNDA_REALM)
          .withEnv("KEYCLOAK_SETUP_USER", KEYCLOAK_USER)
          .withEnv("KEYCLOAK_SETUP_PASSWORD", KEYCLOAK_PASSWORD)
          .withEnv("KEYCLOAK_INIT_ZEEBE_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_NAME", ZEEBE_CLIENT_ID_TENANT_A)
          .withEnv("KEYCLOAK_CLIENTS_0_ID", ZEEBE_CLIENT_ID_TENANT_A)
          .withEnv("KEYCLOAK_CLIENTS_0_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_0_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("KEYCLOAK_CLIENTS_1_NAME", ZEEBE_CLIENT_ID_TENANT_B)
          .withEnv("KEYCLOAK_CLIENTS_1_ID", ZEEBE_CLIENT_ID_TENANT_B)
          .withEnv("KEYCLOAK_CLIENTS_1_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_1_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_1_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_1_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("KEYCLOAK_CLIENTS_2_NAME", ZEEBE_CLIENT_ID_TENANT_A_AND_B)
          .withEnv("KEYCLOAK_CLIENTS_2_ID", ZEEBE_CLIENT_ID_TENANT_A_AND_B)
          .withEnv("KEYCLOAK_CLIENTS_2_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_2_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_2_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_2_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("KEYCLOAK_CLIENTS_3_NAME", ZEEBE_CLIENT_ID_WITHOUT_TENANT)
          .withEnv("KEYCLOAK_CLIENTS_3_ID", ZEEBE_CLIENT_ID_WITHOUT_TENANT)
          .withEnv("KEYCLOAK_CLIENTS_3_SECRET", ZEEBE_CLIENT_SECRET)
          .withEnv("KEYCLOAK_CLIENTS_3_TYPE", "m2m")
          .withEnv("KEYCLOAK_CLIENTS_3_PERMISSIONS_0_RESOURCE_SERVER_ID", ZEEBE_CLIENT_AUDIENCE)
          .withEnv("KEYCLOAK_CLIENTS_3_PERMISSIONS_0_DEFINITION", "write:*")
          .withEnv("IDENTITY_RETRY_ATTEMPTS", "90")
          .withEnv("IDENTITY_RETRY_DELAY_SECONDS", "1")
          .withEnv("IDENTITY_DATABASE_HOST", DATABASE_HOST)
          .withEnv("IDENTITY_DATABASE_PORT", String.valueOf(DATABASE_PORT))
          .withEnv("IDENTITY_DATABASE_NAME", DATABASE_NAME)
          .withEnv("IDENTITY_DATABASE_USERNAME", DATABASE_USER)
          .withEnv("IDENTITY_DATABASE_PASSWORD", DATABASE_PASSWORD)
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

  @AutoCloseResource
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withAdditionalProfile(Profile.IDENTITY_AUTH);

  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(
          () -> Map.of("postgres", POSTGRES, "keycloak", KEYCLOAK, "identity", IDENTITY));

  private String processId;
  private BpmnModelInstance process;

  @BeforeAll
  static void init() throws Exception {
    ZEEBE
        .withGatewayConfig(
            gateway -> {
              gateway.getMultiTenancy().setEnabled(true);
              gateway.getExperimental().getIdentityRequest().setEnabled(true);
              gateway.getSecurity().getAuthentication().setMode(AuthMode.IDENTITY);
            })
        .withProperty(
            "camunda.identity.baseUrl",
            "http://%s:%d".formatted(IDENTITY.getHost(), IDENTITY.getMappedPort(8080)))
        .withProperty(
            "camunda.identity.issuerBackendUrl",
            "http://%s:%d%s"
                .formatted(
                    KEYCLOAK.getHost(), KEYCLOAK.getMappedPort(8080), KEYCLOAK_PATH_CAMUNDA_REALM))
        .withProperty("camunda.identity.audience", ZEEBE_CLIENT_AUDIENCE)
        .withRecordingExporter(true)
        .start()
        .await(TestHealthProbe.READY);

    associateTenantsWithClient(List.of(DEFAULT_TENANT, TENANT_A), ZEEBE_CLIENT_ID_TENANT_A);
    associateTenantsWithClient(List.of(DEFAULT_TENANT, TENANT_B), ZEEBE_CLIENT_ID_TENANT_B);
    associateTenantsWithClient(
        List.of(DEFAULT_TENANT, TENANT_A, TENANT_B), ZEEBE_CLIENT_ID_TENANT_A_AND_B);
  }

  @BeforeEach
  void setup() {
    RecordingExporter.reset();
    processId = Strings.newRandomValidBpmnId();
    process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .endEvent()
            .done();
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/23853")
  void shouldHandleHighLoadOnIdentityTenantRequests() throws InterruptedException {
    // given
    final long processDefinitionKey;
    final ThreadFactory factory =
        Thread.ofVirtual().name("request-factory").uncaughtExceptionHandler((t, e) -> {}).factory();
    try (final var client = createZeebeClient(ZEEBE_CLIENT_ID_TENANT_A);
        final ExecutorService executorService = Executors.newThreadPerTaskExecutor(factory); ) {
      processDefinitionKey =
          client
              .newDeployResourceCommand()
              .addProcessModel(process, "process.bpmn")
              .tenantId(TENANT_A)
              .send()
              .join()
              .getProcesses()
              .stream()
              .map(Process::getProcessDefinitionKey)
              .findFirst()
              .orElseThrow();

      final List<Callable<ZeebeFuture<ProcessInstanceEvent>>> piList = new ArrayList<>();
      for (int i = 0; i < 1000; i++) {
        piList.add(
            () ->
                client
                    .newCreateInstanceCommand()
                    .processDefinitionKey(processDefinitionKey)
                    .tenantId(TENANT_A)
                    .send());
      }

      // when
      final List<Future<ZeebeFuture<ProcessInstanceEvent>>> results =
          executorService.invokeAll(piList);

      // then
      final var exceptionalList =
          results.stream()
              .map(
                  future -> {
                    try {
                      return future.get();
                    } catch (final InterruptedException | ExecutionException e) {
                      throw new RuntimeException(e);
                    }
                  })
              .map(ZeebeFuture::toCompletableFuture)
              .filter(CompletableFuture::isCompletedExceptionally)
              .map(CompletableFuture::exceptionNow)
              .filter(
                  sre ->
                      // A high number of requests may result in the
                      // Broker rejecting the requests with RESOURCE_EXHAUSTED.
                      // This is expected and should not be considered an error.
                      !((StatusRuntimeException) sre)
                          .getStatus()
                          .getCode()
                          .equals(Status.RESOURCE_EXHAUSTED.getCode()))
              .toList();

      // assert that there are no exceptions other than RESOURCE_EXHAUSTED
      // due to the high request load
      assertThat(exceptionalList).isEmpty();
    }
  }

  /**
   * Creates a new Zeebe Client with the given client ID such that Identity can provide the
   * associated tenant IDs. The credentials are cached separately for each test case.
   *
   * @param clientId The client ID to use for the new Zeebe Client
   * @return A new Zeebe Client to use in these tests
   */
  private static ZeebeClient createZeebeClient(final String clientId) {
    return ZEEBE
        .newClientBuilder()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(ZEEBE_CLIENT_SECRET)
                .audience(ZEEBE_CLIENT_AUDIENCE)
                .authorizationServerUrl(
                    "http://localhost:%d%s/protocol/openid-connect/token"
                        .formatted(KEYCLOAK.getFirstMappedPort(), KEYCLOAK_PATH_CAMUNDA_REALM))
                .credentialsCachePath(credentialsCacheDir.resolve(clientId).toString())
                .build())
        .defaultRequestTimeout(Duration.ofSeconds(10))
        .build();
  }

  /**
   * Sets up the association between the given tenant IDs and the given client ID in Keycloak and
   * Identity.
   *
   * @param tenantIds The tenant ids to associate with the client
   * @param clientId The client id to associate the tenants with
   * @throws Exception If an error occurs while performing the database operations
   */
  private static void associateTenantsWithClient(
      final List<String> tenantIds, final String clientId) throws Exception {

    try (final PostgresHelper postgres = new PostgresHelper()) {
      final String accessRuleId = UUID.randomUUID().toString();
      // Create access rule for service account
      try (final var resultSet =
          postgres.executeQuery(
              """
                  INSERT INTO access_rules \
                    (id, member_id, member_type, global) \
                  VALUES ('%s','%s', 'APPLICATION', false) \
                  ON CONFLICT DO NOTHING \
                  RETURNING id"""
                  .formatted(accessRuleId, clientId))) {
        if (!resultSet.next()) {
          throw new IllegalStateException(
              "Expected to find access rule associated to service account.");
        }
      }

      // Create tenant(s) if not already existing,
      // using tenantId for both id and name
      tenantIds.forEach(
          (tenantId) ->
              postgres.execute(
                  """
                      INSERT INTO tenants \
                        (name, tenant_id) \
                      VALUES ('%s', '%s') \
                      ON CONFLICT DO NOTHING"""
                      .formatted(tenantId, tenantId)));

      // Connect tenants to access rule
      tenantIds.forEach(
          tenantId ->
              postgres.execute(
                  """
                      INSERT INTO access_rules_tenants \
                        (tenant_id, access_rule_id) \
                      VALUES ('%s', '%s') \
                      ON CONFLICT DO NOTHING"""
                      .formatted(tenantId, accessRuleId)));
    }
  }

  /**
   * Helper method to determine the Docker image tag for the Camunda Identity version used in Zeebe.
   * If a SNAPSHOT version of Identity is used, the method will return 'SNAPSHOT' instead of the
   * format '8.X.Y-SNAPSHOT', as Identity doesn't provide versioned snapshots of their Docker
   * images.
   *
   * @return a String specifying the Identity Docker image tag
   */
  private static String getIdentityImageTag() {
    final String identityVersion = Identity.class.getPackage().getImplementationVersion();
    final String dockerImageTag =
        System.getProperty("identity.docker.image.version", identityVersion);

    return dockerImageTag.contains("SNAPSHOT") ? IDENTITY_SNAPSHOT_TAG : dockerImageTag;
  }

  /**
   * Helper class to perform queries against the Postgres database. It will automatically create and
   * close the connection. To be used within a try-with-resources block.
   */
  private static final class PostgresHelper implements AutoCloseable {
    private final Supplier<Connection> connector;
    private Connection connection;

    private PostgresHelper() {
      final String jdbcUrl = POSTGRES.getJdbcUrl();
      final String username = POSTGRES.getUsername();
      final String password = POSTGRES.getPassword();
      connector =
          () -> {
            try {
              return DriverManager.getConnection(jdbcUrl, username, password);
            } catch (final SQLException e) {
              throw new RuntimeException(e);
            }
          };
    }

    private ResultSet executeQuery(final String query) {
      if (connection == null) {
        connection = connector.get();
      }
      try {
        final var statement = connection.createStatement();
        return statement.executeQuery(query);
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    private void execute(final String query) {
      if (connection == null) {
        connection = connector.get();
      }
      try (final var statement = connection.createStatement()) {
        statement.execute(query);
      } catch (final SQLException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void close() throws Exception {
      if (connection != null) {
        connection.close();
      }
    }
  }
}
