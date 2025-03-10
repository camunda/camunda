/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_REPO_PROPERTY_NAME;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;
import static org.testcontainers.images.PullPolicy.alwaysPull;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.migration.SemanticVersion;
import io.camunda.operate.util.RetryOperation;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebePort;
import jakarta.annotation.PreDestroy;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Component
public class TestContainerUtil {

  public static final String PROPERTIES_PREFIX = "camunda.operate.";
  public static final String ELS_NETWORK_ALIAS = "elasticsearch";
  public static final int ELS_PORT = 9200;
  public static final String ELS_HOST = "localhost";
  public static final String ELS_SCHEME = "http";
  public static final int POSTGRES_PORT = 5432;
  public static final Integer KEYCLOAK_PORT = 8080;
  public static final Integer IDENTITY_PORT = 8080;
  public static final String IDENTITY_NETWORK_ALIAS = "identity";
  public static final String POSTGRES_NETWORK_ALIAS = "postgres";
  public static final String KEYCLOAK_NETWORK_ALIAS = "keycloak";
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
  private static final String DOCKER_OPERATE_IMAGE_NAME = "camunda/operate";
  private static final Integer OPERATE_HTTP_PORT = 8080;
  private static final String DOCKER_ELASTICSEARCH_IMAGE_NAME =
      "docker.elastic.co/elasticsearch/elasticsearch";
  // There are two cases how we start containers:
  // 1. We start both Elasticsearch and Zeebe as test containers. This way TestContext object stores
  // the names and ports of running containers, and we configure Zeebe with that values.
  // 2. Elasticsearch is started independently on the same "host" machine, but Zeebe is run from
  // test containers and need to connect to that external Elasticsearch instance. We do this by
  // using "Exposing host port" feature of test containers:
  // https://java.testcontainers.org/features/networking/#exposing-host-ports-to-the-container
  // Currently we consider that external Elastic is running on 9200 port.
  private static final String ELS_DOCKER_TESTCONTAINER_URL_DEFAULT =
      "http://host.testcontainers.internal:9200";
  private static final String ZEEBE = "zeebe";
  private static final String KEYCLOAK_ZEEBE_SECRET = "zecret";
  private static final String USER_MEMBER_TYPE = "USER";
  private static final String APPLICATION_MEMBER_TYPE = "APPLICATION";
  private static final String OPERATE = "operate";
  private Network network;
  private ElasticsearchContainer elsContainer;
  private GenericContainer identityContainer;
  private GenericContainer keycloakContainer;
  private PostgreSQLContainer postgreSQLContainer;
  private ZeebeContainer broker;
  private GenericContainer operateContainer;
  private Keycloak keycloakClient;

  public static RestHighLevelClient getEsClient() {
    return new RestHighLevelClient(
        RestClient.builder(new HttpHost(ELS_HOST, ELS_PORT, ELS_SCHEME)));
  }

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
      identityContainer.withEnv("IDENTITY_TENANTS_0_MEMBERS_2_APPLICATION_ID", OPERATE);
      // tenant2 (members: user2, demo)
      identityContainer.withEnv("IDENTITY_TENANTS_1_NAME", TENANT_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_TENANT_ID", TENANT_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_0_TYPE", USER_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_0_USERNAME", KEYCLOAK_USERNAME_2);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_1_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_1_APPLICATION_ID", ZEEBE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_2_TYPE", APPLICATION_MEMBER_TYPE);
      identityContainer.withEnv("IDENTITY_TENANTS_1_MEMBERS_2_APPLICATION_ID", OPERATE);
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

  public void startElasticsearch(final TestContext testContext) {
    LOGGER.info("************ Starting Elasticsearch ************");
    elsContainer =
        new ElasticsearchContainer(
                String.format(
                    "%s:%s", DOCKER_ELASTICSEARCH_IMAGE_NAME, SUPPORTED_ELASTICSEARCH_VERSION))
            .withNetwork(getNetwork())
            .withEnv("xpack.security.enabled", "false")
            .withEnv("path.repo", "~/")
            .withNetworkAliases(ELS_NETWORK_ALIAS)
            .withExposedPorts(ELS_PORT);
    elsContainer.setWaitStrategy(
        new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    elsContainer.start();
    elsContainer.followOutput(new Slf4jLogConsumer(LOGGER));

    testContext.setNetwork(getNetwork());
    testContext.setExternalElsHost(elsContainer.getContainerIpAddress());
    testContext.setExternalElsPort(elsContainer.getMappedPort(ELS_PORT));
    testContext.setInternalElsHost(ELS_NETWORK_ALIAS);
    testContext.setInternalElsPort(ELS_PORT);

    LOGGER.info(
        "************ Elasticsearch started on {}:{} ************",
        testContext.getExternalElsHost(),
        testContext.getExternalElsPort());
  }

  public boolean checkElasctisearchHealth(final TestContext testContext) {
    try {
      final RestHighLevelClient esClient =
          new RestHighLevelClient(
              RestClient.builder(
                  new HttpHost(
                      testContext.getExternalElsHost(), testContext.getExternalElsPort())));
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(5)
          .retryOn(IOException.class, ElasticsearchException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .retryConsumer(
              () -> {
                final ClusterHealthResponse clusterHealthResponse =
                    esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
                return clusterHealthResponse.getStatus().equals(ClusterHealthStatus.GREEN);
              })
          .build()
          .retry();
    } catch (final Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }

  public GenericContainer startOperate(final String version, final TestContext testContext) {
    if (operateContainer == null) {
      LOGGER.info("************ Starting Operate {} ************", version);
      operateContainer = createOperateContainer(version, testContext);
      startOperateContainer(operateContainer, testContext);
      LOGGER.info("************ Operate started  ************");
    } else {
      throw new IllegalStateException("Operate is already started. Call stopOperate first.");
    }
    return operateContainer;
  }

  public GenericContainer createOperateContainer(
      final String dockerImageName, final String version, final TestContext testContext) {
    operateContainer =
        new GenericContainer<>(String.format("%s:%s", dockerImageName, version))
            .withExposedPorts(8080)
            .withNetwork(testContext.getNetwork())
            .withCopyFileToContainer(
                MountableFile.forHostPath(createConfigurationFile(testContext), 0775),
                "/usr/local/operate/config/application.properties")
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(8080)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withStartupTimeout(Duration.ofSeconds(120));
    applyConfiguration(operateContainer, testContext);
    return operateContainer;
  }

  public GenericContainer createOperateContainer(
      final String version, final TestContext testContext) {
    return createOperateContainer(DOCKER_OPERATE_IMAGE_NAME, version, testContext);
  }

  public void startOperateContainer(
      final GenericContainer operateContainer, final TestContext testContext) {
    operateContainer.start();

    testContext.setExternalOperateHost(operateContainer.getHost());
    testContext.setExternalOperatePort(operateContainer.getMappedPort(OPERATE_HTTP_PORT));
  }

  // for newer versions
  private void applyConfiguration(
      final GenericContainer<?> operateContainer, final TestContext testContext) {
    final String elsHost = testContext.getInternalElsHost();
    final Integer elsPort = testContext.getInternalElsPort();
    operateContainer
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", getElasticURL(testContext))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", getElasticURL(testContext))
        .withEnv("SPRING_PROFILES_ACTIVE", "dev");
    final Map<String, String> customEnvs = testContext.getOperateContainerEnvs();
    customEnvs.forEach(operateContainer::withEnv);

    final String zeebeContactPoint = testContext.getInternalZeebeContactPoint();
    if (zeebeContactPoint != null) {
      operateContainer.withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebeContactPoint);
    }
    if (testContext.getZeebeIndexPrefix() != null) {
      operateContainer.withEnv(
          "CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX", testContext.getZeebeIndexPrefix());
    }
  }

  private static String getElasticURL(final TestContext testContext) {
    if (testContext.getInternalElsHost() != null) {
      return String.format(
          "http://%s:%s", testContext.getInternalElsHost(), testContext.getInternalElsPort());
    } else {
      return ELS_DOCKER_TESTCONTAINER_URL_DEFAULT;
    }
  }

  protected Path createConfigurationFile(final TestContext testContext) {
    try {
      final Properties properties =
          getOperateElsProperties(
              testContext.getInternalElsHost(),
              testContext.getInternalElsPort(),
              testContext.getInternalZeebeContactPoint(),
              testContext.getZeebeIndexPrefix());
      final Path tempFile = Files.createTempFile(getClass().getPackage().getName(), ".tmp");
      properties.store(new FileWriter(tempFile.toFile()), null);
      return tempFile;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // for older versions
  protected Properties getOperateElsProperties(
      final String elsHost,
      final Integer elsPort,
      final String zeebeContactPoint,
      final String zeebeIndexPrefix) {
    final Properties properties = new Properties();
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.port", String.valueOf(elsPort));
    properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.port", String.valueOf(elsPort));
    if (zeebeContactPoint != null) {
      properties.setProperty(PROPERTIES_PREFIX + "zeebe.brokerContactPoint", zeebeContactPoint);
      properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.prefix", zeebeIndexPrefix);
    }
    properties.setProperty(PROPERTIES_PREFIX + "archiver.waitPeriodBeforeArchiving", "2m");

    // Operate's test container mounts another "application.properties" file that prevents
    // Spring from loading the default "application.properties" file which by default
    // configures the matching strategy
    properties.setProperty("spring.mvc.pathmatch.matching-strategy", "ANT_PATH_MATCHER");
    properties.setProperty("spring.liquibase.enabled", "false");

    return properties;
  }

  public ZeebeContainer startZeebe(
      final String version,
      final String prefix,
      final Integer partitionCount,
      final boolean multitenancyEnabled,
      final String connectionType) {
    final TestContext testContext =
        new TestContext()
            .setZeebeIndexPrefix(prefix)
            .setPartitionCount(partitionCount)
            .setMultitenancyEnabled(multitenancyEnabled)
            .setConnectionType(connectionType);
    return startZeebe(version, testContext);
  }

  public ZeebeContainer startZeebe(final String version, final TestContext testContext) {
    if (broker == null) {
      final String dockerRepo =
          ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_DOCKER_REPO_PROPERTY_NAME);
      LOGGER.info("************ Starting Zeebe {}:{} ************", dockerRepo, version);
      final long startTime = System.currentTimeMillis();
      Testcontainers.exposeHostPorts(ELS_PORT);
      broker =
          new ZeebeContainer(DockerImageName.parse(String.format("%s:%s", dockerRepo, version)));
      broker.withLogConsumer(new Slf4jLogConsumer(LOGGER));
      if (testContext.getNetwork() != null) {
        broker.withNetwork(testContext.getNetwork());
      }
      if (testContext.getZeebeDataFolder() != null) {
        broker.withFileSystemBind(
            testContext.getZeebeDataFolder().getPath(), "/usr/local/zeebe/data");
      }
      if ("SNAPSHOT".equals(version)) {
        broker.withImagePullPolicy(alwaysPull());
      }

      // from 8.3.0 onwards, Zeebe is run with a non-root user in the container;
      // this user cannot access a mounted volume that is owned by root
      broker.withCreateContainerCmdModifier(cmd -> cmd.withUser("root"));

      if ("SNAPSHOT".equals(version)
          || "current-test".equals(version)
          || SemanticVersion.fromVersion(version).isAtLeast("8.8.0")) {
        configureCamundaExporter(testContext);
      } else {
        configureElasticsearchExporter(testContext);
      }
      broker
          .withEnv("JAVA_OPTS", "-Xss256k -XX:+TieredCompilation -XX:TieredStopAtLevel=1")
          .withEnv("ZEEBE_LOG_LEVEL", "DEBUG")
          .withEnv("ATOMIX_LOG_LEVEL", "ERROR")
          .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
          .withEnv("ZEEBE_BROKER_DATA_DISKUSAGEREPLICATIONWATERMARK", "0.99")
          .withEnv("ZEEBE_BROKER_DATA_DISKUSAGECOMMANDWATERMARK", "0.98")
          .withEnv("ZEEBE_BROKER_DATA_SNAPSHOTPERIOD", "1m");

      if (testContext.getPartitionCount() != null) {
        broker.withEnv(
            "ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT",
            String.valueOf(testContext.getPartitionCount()));
      }
      if (testContext.isMultitenancyEnabled() != null) {
        broker.withEnv(
            "ZEEBE_BROKER_GATEWAY_MULTITENANCY_ENABLED",
            String.valueOf(testContext.isMultitenancyEnabled()));
        if (testContext.isMultitenancyEnabled()) {
          broker
              .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_MODE", "identity")
              .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_TYPE", "keycloak")
              .withEnv(
                  "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_ISSUERBACKENDURL",
                  IdentityTester.testContext.getInternalKeycloakBaseUrl()
                      + "/auth/realms/camunda-platform")
              .withEnv(
                  "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_AUDIENCE", "zeebe-api")
              .withEnv(
                  "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_BASEURL",
                  IdentityTester.testContext.getInternalIdentityBaseUrl());
        }
      }
      broker.start();

      LOGGER.info(
          "\n====\nBroker startup time: {}\n====\n", (System.currentTimeMillis() - startTime));

      testContext.setInternalZeebeContactPoint(
          broker.getInternalAddress(ZeebePort.GATEWAY.getPort()));
      testContext.setExternalZeebeContactPoint(
          broker.getExternalAddress(ZeebePort.GATEWAY.getPort()));
    } else {
      throw new IllegalStateException("Broker is already started. Call stopZeebe first.");
    }
    return broker;
  }

  private void configureCamundaExporter(final TestContext testContext) {
    broker
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME",
            "io.camunda.exporter.CamundaExporter")
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE",
            testContext.getConnectionType())
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL", getElasticURL(testContext))
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_DELAY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE", "1");
    if (testContext.getZeebeIndexPrefix() != null) {
      broker.withEnv(
          "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_PREFIX",
          testContext.getZeebeIndexPrefix());
    }
  }

  private void configureElasticsearchExporter(final TestContext testContext) {
    broker
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", getElasticURL(testContext))
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_DEPLOYMENTDISTRIBUTION", "false")
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_MESSAGESTARTSUBSCRIPTION", "false")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_TIMER", "false")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PROCESSINSTANCECREATION", "false")
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PROCESSINSTANCEMODIFICATION", "false")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_ESCALATION", "false")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PROCESSEVENT", "false");

    if (testContext.getZeebeIndexPrefix() != null) {
      broker.withEnv(
          "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX",
          testContext.getZeebeIndexPrefix());
    }
  }

  public void stopZeebeAndOperate(final TestContext testContext) {
    stopZeebe(testContext);
    stopOperate(testContext);
  }

  protected void stopZeebe(final TestContext testContext) {
    stopZeebe(testContext, null);
  }

  public void stopZeebe(final TestContext testContext, final File tmpFolder) {
    stopZeebe(tmpFolder);
    testContext.setInternalZeebeContactPoint(null);
    testContext.setExternalZeebeContactPoint(null);
  }

  @SuppressWarnings("checkstyle:NestedIfDepth")
  public void stopZeebe(final File tmpFolder) {
    if (broker != null) {
      try {
        if (tmpFolder != null && tmpFolder.listFiles().length > 0) {
          boolean found = false;
          int attempts = 0;
          while (!found && attempts < 10) {
            // check for snapshot existence
            final List<Path> files =
                Files.walk(Paths.get(tmpFolder.toURI()))
                    .filter(p -> p.getFileName().endsWith("snapshots"))
                    .collect(Collectors.toList());
            if (files.size() == 1 && Files.isDirectory(files.get(0))) {
              if (Files.walk(files.get(0)).count() > 1) {
                found = true;
                LOGGER.debug(
                    "Zeebe snapshot was found in "
                        + Files.walk(files.get(0)).findFirst().toString());
              }
            }
            if (!found) {
              sleepFor(10000L);
            }
            attempts++;
          }
          if (!found) {
            throw new AssertionError("Zeebe snapshot was never taken");
          }
        }
      } catch (final IOException e) {
        throw new RuntimeException(e);
      } finally {
        try {
          broker.shutdownGracefully(Duration.ofSeconds(3));
        } catch (final Exception ex) {
          LOGGER.error("broker.shutdownGracefully failed", ex);
          // ignore
        }
        try {
          broker.stop();
        } catch (final Exception ex) {
          LOGGER.error("broker.stop failed", ex);
          // ignore
        }
        broker = null;
      }
    }
  }

  protected void stopOperate(final TestContext testContext) {
    if (operateContainer != null) {
      operateContainer.close();
      operateContainer = null;
    }
    testContext.setExternalOperateHost(null);
    testContext.setExternalOperatePort(null);
  }

  public Network getNetwork() {
    if (network == null) {
      network = Network.newNetwork();
    }
    return network;
  }

  @PreDestroy
  public void stopElasticsearch() {
    stopEls();
    closeNetwork();
  }

  private void stopEls() {
    if (elsContainer != null) {
      elsContainer.stop();
    }
  }

  private void closeNetwork() {
    if (network != null) {
      network.close();
      network = null;
    }
  }
}
