/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.exporter.CamundaExporter;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.RetryOperation;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.keycloak.admin.client.Keycloak;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@Component
@Configuration
@EnableResilientMethods
public class TestContainerUtil {

  public static final String ELS_NETWORK_ALIAS = "elasticsearch";
  public static final String OS_NETWORK_ALIAS = "opensearch";

  public static final String KEYCLOAK_NETWORK_ALIAS = "keycloak";

  public static final String POSTGRES_NETWORK_ALIAS = "postgres";
  public static final String IDENTITY_NETWORK_ALIAS = "identity";
  public static final int ELS_PORT = 9200;
  public static final int OS_PORT = 9200;
  public static final int POSTGRES_PORT = 5432;
  public static final Integer KEYCLOAK_PORT = 8080;
  public static final Integer IDENTITY_PORT = 8080;
  public static final String KEYCLOAK_INIT_OPERATE_SECRET = "the-cake-is-alive";
  public static final String KEYCLOAK_INIT_TASKLIST_SECRET = "the-cake-is-alive";
  public static final String KEYCLOAK_USERNAME = "demo";
  public static final String KEYCLOAK_PASSWORD = "demo";
  public static final String KEYCLOAK_USERNAME_2 = "user2";
  public static final String KEYCLOAK_PASSWORD_2 = "user2";
  public static final String KEYCLOAK_USERS_0_ROLES_0 = "Identity";
  public static final String KEYCLOAK_USERS_0_ROLES_1 = "Tasklist";
  public static final String KEYCLOAK_USERS_0_ROLES_2 = "Operate";
  public static final String IDENTITY_DATABASE_HOST = "postgres";
  public static final String IDENTITY_DATABASE_NAME = "identity";
  public static final String IDENTITY_DATABASE_USERNAME = "identity";
  public static final String IDENTITY_DATABASE_PASSWORD = "t2L@!AqSMg8%I%NmHM";
  public static final String TENANT_1 = "tenant_1";
  public static final String TENANT_2 = "tenant_2";
  private static final Logger LOGGER = LoggerFactory.getLogger(TestContainerUtil.class);
  private static final Integer TASKLIST_HTTP_PORT = 8080;
  private static final Integer TASKLIST_MGMT_HTTP_PORT = 9600;
  private static final String DOCKER_ELASTICSEARCH_IMAGE_NAME =
      "docker.elastic.co/elasticsearch/elasticsearch";
  private static final String DOCKER_OPENSEARCH_IMAGE_NAME = "opensearchproject/opensearch";
  private static final String DOCKER_OPENSEARCH_IMAGE_VERSION = "2.17.0";
  private static final String ZEEBE = "zeebe";
  private static final String KEYCLOAK_ZEEBE_SECRET = "zecret";
  private static final String USER_MEMBER_TYPE = "USER";
  private static final String APPLICATION_MEMBER_TYPE = "APPLICATION";
  private static final String TASKLIST = "tasklist";
  private ElasticsearchContainer elsContainer;
  private OpenSearchContainer osContainer;
  private TestStandaloneBroker broker;
  private GenericContainer tasklistContainer;
  private GenericContainer identityContainer;
  private GenericContainer keycloakContainer;
  private PostgreSQLContainer postgreSQLContainer;

  private Keycloak keycloakClient;

  public void startIdentity(
      final TestContext testContext, final String version, final boolean multiTenancyEnabled) {
    startPostgres(testContext);
    startKeyCloak(testContext);

    LOGGER.info("************ Starting Identity ************");
    identityContainer =
        new GenericContainer<>(String.format("%s:%s", "camunda/identity", version))
            .withExposedPorts(IDENTITY_PORT)
            .withNetwork(Network.SHARED)
            .withNetworkAliases(IDENTITY_NETWORK_ALIAS);

    identityContainer.withEnv("SERVER_PORT", String.valueOf(IDENTITY_PORT));
    identityContainer.withEnv("KEYCLOAK_URL", testContext.getInternalKeycloakBaseUrl() + "/auth");
    identityContainer.withEnv(
        "IDENTITY_AUTH_PROVIDER_BACKEND_URL",
        String.format(
            "http://%s:%s/auth/realms/camunda-platform",
            testContext.getInternalKeycloakHost(), testContext.getInternalKeycloakPort()));
    identityContainer.withEnv("IDENTITY_DATABASE_HOST", IDENTITY_DATABASE_HOST);
    identityContainer.withEnv("IDENTITY_DATABASE_PORT", String.valueOf(POSTGRES_PORT));
    identityContainer.withEnv("IDENTITY_DATABASE_NAME", IDENTITY_DATABASE_NAME);
    identityContainer.withEnv("IDENTITY_DATABASE_USERNAME", IDENTITY_DATABASE_USERNAME);
    identityContainer.withEnv("IDENTITY_DATABASE_PASSWORD", IDENTITY_DATABASE_PASSWORD);
    identityContainer.withEnv("KEYCLOAK_INIT_OPERATE_SECRET", KEYCLOAK_INIT_OPERATE_SECRET);
    identityContainer.withEnv("KEYCLOAK_INIT_OPERATE_ROOT_URL", "http://localhost:8081");
    identityContainer.withEnv("KEYCLOAK_INIT_TASKLIST_SECRET", KEYCLOAK_INIT_TASKLIST_SECRET);
    identityContainer.withEnv("KEYCLOAK_INIT_TASKLIST_ROOT_URL", "http://localhost:8082");
    identityContainer.withEnv("KEYCLOAK_INIT_ZEEBE_SECRET", KEYCLOAK_ZEEBE_SECRET);
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_NAME", ZEEBE);
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_ID", ZEEBE);
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_SECRET", KEYCLOAK_ZEEBE_SECRET);
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_TYPE", "M2M");
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_RESOURCE_SERVER_ID", "zeebe-api");
    identityContainer.withEnv("KEYCLOAK_CLIENTS_0_PERMISSIONS_0_DEFINITION", "write:*");
    identityContainer.withEnv("KEYCLOAK_USERS_0_USERNAME", KEYCLOAK_USERNAME);
    identityContainer.withEnv("KEYCLOAK_USERS_0_PASSWORD", KEYCLOAK_PASSWORD);
    identityContainer.withEnv("KEYCLOAK_USERS_0_ROLES_0", KEYCLOAK_USERS_0_ROLES_0);
    identityContainer.withEnv("KEYCLOAK_USERS_0_ROLES_1", KEYCLOAK_USERS_0_ROLES_1);
    identityContainer.withEnv("KEYCLOAK_USERS_0_ROLES_2", KEYCLOAK_USERS_0_ROLES_2);
    identityContainer.withEnv("KEYCLOAK_USERS_1_USERNAME", KEYCLOAK_USERNAME_2);
    identityContainer.withEnv("KEYCLOAK_USERS_1_PASSWORD", KEYCLOAK_PASSWORD_2);
    identityContainer.withEnv("KEYCLOAK_USERS_1_ROLES_0", KEYCLOAK_USERS_0_ROLES_0);
    identityContainer.withEnv("KEYCLOAK_USERS_1_ROLES_1", KEYCLOAK_USERS_0_ROLES_1);
    identityContainer.withEnv("KEYCLOAK_USERS_1_ROLES_2", KEYCLOAK_USERS_0_ROLES_2);
    identityContainer.withEnv("RESOURCE_PERMISSIONS_ENABLED", "true");
    identityContainer.withEnv("MULTITENANCY_ENABLED", String.valueOf(multiTenancyEnabled));
    if (multiTenancyEnabled) {
      // tenant1 (members: demo)
      identityContainer.withEnv("IDENTITY_TENANTS_0_NAME", TENANT_1);
      identityContainer.withEnv("IDENTITY_TENANTS_0_TENANT_ID", TENANT_1);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_0_TYPE", USER_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_0_USERNAME", KEYCLOAK_USERNAME);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_1_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_1_APPLICATION_ID", ZEEBE);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_2_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_2_APPLICATION_ID", TASKLIST);
      // tenant2 (members: user2, demo)
      identityContainer.withEnv("IDENTITY_TENANTS_1_NAME", TENANT_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_TENANT_ID", TENANT_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_0_TYPE", USER_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_0_USERNAME", KEYCLOAK_USERNAME_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_1_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_1_APPLICATION_ID", ZEEBE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_2_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_2_APPLICATION_ID", TASKLIST);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_3_TYPE", USER_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_3_USERNAME", KEYCLOAK_USERNAME);
    }

    identityContainer.start();

    testContext.setExternalIdentityHost(identityContainer.getContainerIpAddress());
    testContext.setExternalIdentityPort(identityContainer.getMappedPort(IDENTITY_PORT));
    testContext.setInternalIdentityHost(IDENTITY_NETWORK_ALIAS);
    testContext.setInternalIdentityPort(IDENTITY_PORT);
    LOGGER.info(
        "************ Identity started on {}:{} ************",
        testContext.getExternalIdentityHost(),
        testContext.getExternalIdentityPort());
  }

  public void startKeyCloak(final TestContext testContext) {
    LOGGER.info("************ Starting Keycloak ************");
    keycloakContainer =
        new GenericContainer<>(DockerImageName.parse("bitnami/keycloak:22.0.1"))
            .withExposedPorts(KEYCLOAK_PORT)
            .withEnv(
                Map.of(
                    "KEYCLOAK_HTTP_RELATIVE_PATH",
                    "/auth",
                    "KEYCLOAK_DATABASE_USER",
                    IDENTITY_DATABASE_USERNAME,
                    "KEYCLOAK_DATABASE_PASSWORD",
                    IDENTITY_DATABASE_PASSWORD,
                    "KEYCLOAK_DATABASE_NAME",
                    IDENTITY_DATABASE_NAME,
                    "KEYCLOAK_ADMIN_USER",
                    "admin",
                    "KEYCLOAK_ADMIN_PASSWORD",
                    "admin",
                    "KEYCLOAK_DATABASE_HOST",
                    POSTGRES_NETWORK_ALIAS))
            .dependsOn(postgreSQLContainer)
            .withNetwork(Network.SHARED)
            .withNetworkAliases(KEYCLOAK_NETWORK_ALIAS);
    keycloakContainer.start();
    testContext.setExternalKeycloakHost(keycloakContainer.getContainerIpAddress());
    testContext.setExternalKeycloakPort(keycloakContainer.getFirstMappedPort());
    testContext.setInternalKeycloakHost(KEYCLOAK_NETWORK_ALIAS);
    testContext.setInternalKeycloakPort(KEYCLOAK_PORT);

    LOGGER.info(
        "************ Keycloak started on {}:{} ************",
        testContext.getExternalKeycloakHost(),
        testContext.getExternalKeycloakPort());

    keycloakClient =
        Keycloak.getInstance(
            testContext.getExternalKeycloakBaseUrl() + "/auth",
            "master",
            "admin",
            "admin",
            "admin-cli");
  }

  public void startPostgres(final TestContext testContext) {
    LOGGER.info("************ Starting Postgres ************");
    postgreSQLContainer =
        new PostgreSQLContainer("postgres:15.2-alpine")
            .withDatabaseName(IDENTITY_DATABASE_NAME)
            .withUsername(IDENTITY_DATABASE_USERNAME)
            .withPassword(IDENTITY_DATABASE_PASSWORD);
    postgreSQLContainer.withNetwork(Network.SHARED);
    postgreSQLContainer.withExposedPorts(POSTGRES_PORT);
    postgreSQLContainer.withNetworkAliases(POSTGRES_NETWORK_ALIAS);
    postgreSQLContainer.start();

    testContext.setExternalPostgresHost(postgreSQLContainer.getContainerIpAddress());
    testContext.setExternalPostgresPort(postgreSQLContainer.getMappedPort(POSTGRES_PORT));
    testContext.setInternalPostgresHost(POSTGRES_NETWORK_ALIAS);
    testContext.setInternalPostgresPort(POSTGRES_PORT);
    LOGGER.info(
        "************ Postgres started on {}:{} ************",
        testContext.getExternalPostgresHost(),
        testContext.getExternalPostgresPort());
  }

  public void startOpenSearch(final TestContext testContext) {
    LOGGER.info("************ Starting OpenSearch ************");
    osContainer =
        (OpenSearchContainer)
            new OpenSearchContainer(
                    String.format(
                        "%s:%s", DOCKER_OPENSEARCH_IMAGE_NAME, DOCKER_OPENSEARCH_IMAGE_VERSION))
                .withNetwork(Network.SHARED)
                .withEnv("path.repo", "~/")
                .withNetworkAliases(OS_NETWORK_ALIAS)
                .withExposedPorts(OS_PORT);
    osContainer.setWaitStrategy(
        new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    osContainer.start();

    testContext.setNetwork(Network.SHARED);
    testContext.setExternalOsHost(osContainer.getContainerIpAddress());
    testContext.setExternalOsPort(osContainer.getMappedPort(OS_PORT));
    testContext.setInternalOsHost(OS_NETWORK_ALIAS);
    testContext.setInternalOsPort(OS_PORT);

    LOGGER.info(
        "************ OpenSearch started on {}:{} ************",
        testContext.getExternalOsHost(),
        testContext.getExternalOsPort());
  }

  public void startElasticsearch(final TestContext testContext) {
    LOGGER.info("************ Starting Elasticsearch ************");
    elsContainer =
        new ElasticsearchContainer(
                String.format(
                    "%s:%s", DOCKER_ELASTICSEARCH_IMAGE_NAME, SUPPORTED_ELASTICSEARCH_VERSION))
            .withNetwork(Network.SHARED)
            .withEnv("xpack.security.enabled", "false")
            .withEnv("path.repo", "~/")
            .withEnv("action.destructive_requires_name", "false")
            .withNetworkAliases(ELS_NETWORK_ALIAS)
            .withExposedPorts(ELS_PORT);
    elsContainer.setWaitStrategy(
        new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    elsContainer.start();

    testContext.setNetwork(Network.SHARED);
    testContext.setExternalElsHost(elsContainer.getContainerIpAddress());
    testContext.setExternalElsPort(elsContainer.getMappedPort(ELS_PORT));
    testContext.setInternalElsHost(ELS_NETWORK_ALIAS);
    testContext.setInternalElsPort(ELS_PORT);

    LOGGER.info(
        "************ Elasticsearch started on {}:{} ************",
        testContext.getExternalElsHost(),
        testContext.getExternalElsPort());
  }

  public String getIdentityClientSecret() {
    try {
      return RetryOperation.<String>newBuilder()
          .noOfRetry(5)
          .retryOn(NotFoundException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .message("Trying to get Identity keycloak client secret")
          .retryConsumer(
              () ->
                  keycloakClient
                      .realm("camunda-platform")
                      .clients()
                      .findByClientId("camunda-identity")
                      .get(0)
                      .getSecret())
          .build()
          .retry();
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Retryable(maxRetries = 5, delay = 3000, includes = TasklistRuntimeException.class)
  public void checkElasticsearchHealth(final TestContext testContext) {
    try {
      final RestClient restClient =
          RestClient.builder(
                  new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort()))
              .build();
      final ElasticsearchTransport transport =
          new RestClientTransport(restClient, new JacksonJsonpMapper());
      final ElasticsearchClient esClient = new ElasticsearchClient(transport);
      final HealthResponse healthResponse = esClient.cluster().health();
      final HealthStatus healthStatus = healthResponse.status();
      final boolean isHealthy = HealthStatus.Green.equals(healthStatus);
      if (isHealthy) {
        LOGGER.info("ElasticSearch cluster is up & running, health status: '{}'", healthStatus);
      } else {
        LOGGER.warn("ElasticSearch cluster health status is : '{}'", healthStatus);
      }
    } catch (final IOException | ElasticsearchException e) {
      throw new TasklistRuntimeException("Elasticsearch health check failed", e);
    }
  }

  @Retryable(maxRetries = 5, delay = 3000, includes = TasklistRuntimeException.class)
  public void checkOpenSearchHealth(final OpenSearchClient osClient) {
    try {
      final org.opensearch.client.opensearch.cluster.HealthResponse healthResponse =
          osClient.cluster().health();
      final org.opensearch.client.opensearch._types.HealthStatus healthStatus =
          healthResponse.status();
      final boolean isHealthy =
          org.opensearch.client.opensearch._types.HealthStatus.Green.equals(healthStatus);
      if (isHealthy) {
        LOGGER.info("OpenSearch cluster is up & running, health status: '{}'", healthStatus);
      } else {
        LOGGER.warn("OpenSearch cluster health status is : '{}'", healthStatus);
      }
    } catch (final IOException | OpenSearchException ex) {
      throw new TasklistRuntimeException("OpenSearch health check failed", ex);
    }
  }

  public GenericContainer createTasklistContainer(final TestContext testContext) {
    return createTasklistContainer(ContainerVersionsUtil.getTasklistDockerImageName(), testContext);
  }

  public GenericContainer createTasklistContainer(
      final DockerImageName dockerImageName, final TestContext testContext) {
    final String version = dockerImageName.getVersionPart();
    final int managementPort = getTasklistManagementPort(version);
    final Integer[] exposedPorts =
        new HashSet<>(List.of(TASKLIST_HTTP_PORT, managementPort)).toArray(Integer[]::new);
    tasklistContainer =
        new GenericContainer<>(dockerImageName)
            .withExposedPorts(exposedPorts)
            .withAccessToHost(true)
            .withExtraHost("host.testcontainers.internal", "host-gateway")
            .withNetwork(Network.SHARED)
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(managementPort)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withStartupTimeout(Duration.ofSeconds(120));
    applyConfiguration(tasklistContainer, testContext);
    return tasklistContainer;
  }

  private int getTasklistManagementPort(final String version) {
    return version.compareTo("8.6.0") >= 0 ? TASKLIST_MGMT_HTTP_PORT : TASKLIST_HTTP_PORT;
  }

  public void startTasklistContainer(
      final GenericContainer tasklistContainer, final TestContext testContext) {
    tasklistContainer.start();
    final DockerImageName dockerImageName =
        DockerImageName.parse(tasklistContainer.getDockerImageName());
    final String version = dockerImageName.getVersionPart();
    testContext.setExternalTasklistHost(tasklistContainer.getHost());
    testContext.setExternalTasklistPort(tasklistContainer.getMappedPort(TASKLIST_HTTP_PORT));
    testContext.setExternalTasklistMgmtPort(
        tasklistContainer.getMappedPort(getTasklistManagementPort(version)));
  }

  // for newer versions
  private void applyConfiguration(
      final GenericContainer<?> tasklistContainer, final TestContext testContext) {
    final var indexPrefix =
        testContext.getIndexPrefix() != null ? testContext.getIndexPrefix() : "";
    if (TestUtil.isOpenSearch()) {
      final String osHost = testContext.getInternalOsHost();
      final Integer osPort = testContext.getInternalOsPort();
      final String osUrl = String.format("http://%s:%s", osHost, osPort);
      tasklistContainer
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_OPENSEARCH_URL", osUrl)
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "opensearch")
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_OPENSEARCH_INDEXPREFIX", indexPrefix)
          // ---
          .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_HOST", osHost)
          .withEnv("CAMUNDA_TASKLIST_OPENSEARCH_PORT", String.valueOf(osPort));
    } else {
      final String elsHost = testContext.getInternalElsHost();
      final Integer elsPort = testContext.getInternalElsPort();
      final String esUrl = String.format("http://%s:%s", elsHost, elsPort);
      tasklistContainer
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", esUrl)
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", "elasticsearch")
          .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", indexPrefix)
          // ---
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_HOST", elsHost)
          .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_PORT", String.valueOf(elsPort))
          // ---
          .withEnv("SPRING_PROFILES_ACTIVE", "consolidated-auth")
          .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "false")
          .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
          .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_METHOD", "BASIC")
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME", "demo")
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD", "demo")
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME", "Demo")
          .withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL", "demo@example.com");
    }

    final String zeebeContactPoint = testContext.getInternalZeebeContactPoint();
    if (zeebeContactPoint != null) {
      tasklistContainer.withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebeContactPoint);
    }
  }

  public TestStandaloneBroker startStandaloneBroker(final TestContext<?> testContext) {
    if (broker == null) {
      broker =
          new TestStandaloneBroker()
              .withGatewayEnabled(true)
              .withSecurityConfig(
                  cfg -> {
                    cfg.getAuthentication().setUnprotectedApi(true);
                    cfg.getAuthorizations().setEnabled(false);
                    final var user = new ConfiguredUser("demo", "demo", "Demo", "demo@example.com");
                    cfg.getInitialization().setUsers(List.of(user));
                  });
      LOGGER.info("************ Starting StandaloneBroker ************");
      addConfig(broker, testContext);
      broker.withCreateSchema(testContext.isCreateSchema());
      broker.start();
      LOGGER.info("************ StandaloneBroker started  ************");

      testContext.setInternalZeebeContactPoint(
          String.format(
              "host.testcontainers.internal:%d", broker.mappedPort(TestZeebePort.GATEWAY)));
      testContext.setZeebeGrpcAddress(broker.grpcAddress());
    } else {
      throw new IllegalStateException("Broker is already started. Call stopZeebe first.");
    }
    return broker;
  }

  protected void addConfig(final TestStandaloneBroker zeebeBroker, final TestContext testContext) {
    final var url =
        TestUtil.isOpenSearch()
            ? "http://%s:%s"
                .formatted(testContext.getExternalOsHost(), testContext.getExternalOsPort())
            : "http://%s:%s"
                .formatted(testContext.getExternalElsHost(), testContext.getExternalElsPort());
    final var type = TestUtil.isOpenSearch() ? "opensearch" : "elasticsearch";

    zeebeBroker.withExporter(
        CamundaExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of(
                      "url",
                      url,
                      "type",
                      type,
                      "indexPrefix",
                      testContext.getIndexPrefix() != null ? testContext.getIndexPrefix() : "",
                      "index",
                      Map.of(
                          "prefix",
                          testContext.getIndexPrefix() != null ? testContext.getIndexPrefix() : ""),
                      "bulk",
                      Map.of("size", 1)),
                  "history",
                  Map.of("waitPeriodBeforeArchiving", "1s")));
        });

    zeebeBroker
        .withSecondaryStorageType(
            TestUtil.isOpenSearch()
                ? SecondaryStorageType.opensearch
                : SecondaryStorageType.elasticsearch)
        .withUnifiedConfig(
            cfg -> {
              if (TestUtil.isOpenSearch()) {
                cfg.getData().getSecondaryStorage().getOpensearch().setUrl(url);
                if (testContext.getIndexPrefix() != null) {
                  cfg.getData()
                      .getSecondaryStorage()
                      .getOpensearch()
                      .setIndexPrefix(testContext.getIndexPrefix());
                }
              } else {
                cfg.getData().getSecondaryStorage().getElasticsearch().setUrl(url);
                if (testContext.getIndexPrefix() != null) {
                  cfg.getData()
                      .getSecondaryStorage()
                      .getElasticsearch()
                      .setIndexPrefix(testContext.getIndexPrefix());
                }
              }
            });
  }

  public void stopZeebeAndTasklist(final TestContext testContext) {
    stopZeebe(testContext);
    stopTasklist(testContext);
  }

  public void stopIdentity(final TestContext testcontext) {
    if (identityContainer != null) {
      identityContainer.close();
    }
    if (postgreSQLContainer != null) {
      postgreSQLContainer.close();
    }
    if (keycloakContainer != null) {
      keycloakContainer.close();
    }
  }

  protected void stopZeebe(final TestContext testContext) {
    if (broker != null) {
      broker.close();
      broker = null;
    }
    testContext.setInternalZeebeContactPoint(null);
    testContext.setZeebeGrpcAddress(null);
  }

  protected void stopTasklist(final TestContext testContext) {
    if (tasklistContainer != null) {
      tasklistContainer.close();
      tasklistContainer = null;
    }
    testContext.setExternalTasklistHost(null);
    testContext.setExternalTasklistPort(null);
    testContext.setExternalTasklistMgmtPort(null);
  }

  @PreDestroy
  public void stopElasticsearch() {
    stopEls();
  }

  private void stopEls() {
    if (elsContainer != null) {
      elsContainer.stop();
    }
  }
}
