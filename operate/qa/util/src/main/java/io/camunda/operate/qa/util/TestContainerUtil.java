/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_ELASTICSEARCH_VERSION;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.RetryOperation;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import jakarta.annotation.PreDestroy;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.MountableFile;

@Component
public class TestContainerUtil {

  public static final String PROPERTIES_PREFIX = "camunda.operate.";
  public static final String ELS_NETWORK_ALIAS = "elasticsearch";
  public static final String DB_TYPE_ELASTICSEARCH = "elasticsearch";
  public static final int ELS_PORT = 9200;
  private static final Logger LOGGER = LoggerFactory.getLogger(TestContainerUtil.class);
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
  private ElasticsearchContainer elsContainer;
  private TestStandaloneBroker broker;
  private GenericContainer operateContainer;

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
    elsContainer.followOutput(new Slf4jLogConsumer(LOGGER));

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

  public boolean checkElasticsearchHealth(
      final TestContext testContext, final ElasticsearchClient esClient) {
    try {
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(5)
          .retryOn(IOException.class, ElasticsearchException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .retryConsumer(
              () -> {
                final HealthResponse clusterHealthResponse = esClient.cluster().health();
                return clusterHealthResponse.status() == HealthStatus.Green;
              })
          .build()
          .retry();
    } catch (final Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }

  public GenericContainer createOperateContainer(
      final String dockerImageName, final String version, final TestContext testContext) {
    operateContainer =
        new GenericContainer<>(String.format("%s:%s", dockerImageName, version))
            .withExposedPorts(8080)
            .withNetwork(testContext.getNetwork())
            .withExtraHost("host.testcontainers.internal", "host-gateway")
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

  public void startOperateContainer(
      final GenericContainer operateContainer, final TestContext testContext) {
    operateContainer.start();

    testContext.setExternalOperateHost(operateContainer.getHost());
    testContext.setExternalOperatePort(operateContainer.getMappedPort(OPERATE_HTTP_PORT));
  }

  // for newer versions
  private void applyConfiguration(
      final GenericContainer<?> operateContainer, final TestContext testContext) {
    operateContainer
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", getElasticURL(testContext))
        .withEnv("CAMUNDA_DATA_SECONDARYSTORAGE_OPENSEARCH_URL", getElasticURL(testContext))
        .withEnv("SPRING_PROFILES_ACTIVE", "dev, consolidated-auth")
        .withEnv("CAMUNDA_OPERATE_ZEEBE_COMPATIBILITY_ENABLED", "true")
        .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "false")
        .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_METHOD", "BASIC")
        .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false");
    final Map<String, String> customEnvs = testContext.getOperateContainerEnvs();
    customEnvs.forEach(operateContainer::withEnv);

    final String zeebeContactPoint = testContext.getInternalZeebeContactPoint();
    if (zeebeContactPoint != null) {
      operateContainer.withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebeContactPoint);
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
              testContext.getInternalZeebeContactPoint());
      final Path tempFile = Files.createTempFile(getClass().getPackage().getName(), ".tmp");
      properties.store(new FileWriter(tempFile.toFile()), null);
      return tempFile;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // for older versions
  protected Properties getOperateElsProperties(
      final String elsHost, final Integer elsPort, final String zeebeContactPoint) {
    final Properties properties = new Properties();
    properties.setProperty("camunda.data.secondary-storage.type", DB_TYPE_ELASTICSEARCH);

    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.port", String.valueOf(elsPort));
    if (zeebeContactPoint != null) {
      properties.setProperty(PROPERTIES_PREFIX + "zeebe.brokerContactPoint", zeebeContactPoint);
    }
    properties.setProperty(PROPERTIES_PREFIX + "archiver.waitPeriodBeforeArchiving", "2m");

    // Operate's test container mounts another "application.properties" file that prevents
    // Spring from loading the default "application.properties" file which by default
    // configures the matching strategy
    properties.setProperty("spring.mvc.pathmatch.matching-strategy", "ANT_PATH_MATCHER");
    properties.setProperty("spring.liquibase.enabled", "false");

    return properties;
  }

  public TestStandaloneBroker startZeebe(final TestContext testContext) {
    if (broker == null) {
      broker =
          new TestStandaloneBroker()
              .withAdditionalProperties(
                  Map.of(
                      "zeebe.log.level",
                      "ERROR",
                      "atomix.log.level",
                      "ERROR",
                      "zeebe.clock.controlled",
                      "true"))
              .withGatewayEnabled(true)
              .withDataConfig(data -> data.setSnapshotPeriod(Duration.ofMinutes(1)))
              .withSecurityConfig(
                  cfg -> {
                    cfg.getAuthentication().setUnprotectedApi(true);
                    cfg.getAuthorizations().setEnabled(false);
                    final var user = new ConfiguredUser("demo", "demo", "Demo", "demo@example.com");
                    cfg.getInitialization().setUsers(List.of(user));
                    if (testContext.isMultitenancyEnabled() != null) {
                      cfg.getMultiTenancy().setChecksEnabled(testContext.isMultitenancyEnabled());
                    }
                  });

      configureCamundaExporter(testContext);

      if (testContext.getPartitionCount() != null) {
        broker.withClusterConfig(
            cluster -> cluster.setPartitionCount(testContext.getPartitionCount()));
      }

      broker.start();

      testContext.setInternalZeebeContactPoint(
          String.format(
              "host.testcontainers.internal:%d", broker.mappedPort(TestZeebePort.GATEWAY)));
      testContext.setZeebeGrpcAddress(broker.grpcAddress());
    } else {
      throw new IllegalStateException("Broker is already started. Call stopZeebe first.");
    }
    return broker;
  }

  private void configureCamundaExporter(final TestContext testContext) {
    final String dbType = testContext.getDatabaseType();

    final String dbUrl =
        String.format(
            "http://%s:%s", testContext.getExternalElsHost(), testContext.getExternalElsPort());

    broker.withExporter(
        CamundaExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of(
                      "url",
                      dbUrl,
                      "type",
                      dbType,
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

    final var secondaryStorageType = SecondaryStorageType.valueOf(dbType);

    broker
        .withSecondaryStorageType(secondaryStorageType)
        .withUnifiedConfig(
            cfg -> {
              if (secondaryStorageType.isOpenSearch()) {
                cfg.getData().getSecondaryStorage().getOpensearch().setUrl(dbUrl);
                if (testContext.getIndexPrefix() != null) {
                  cfg.getData()
                      .getSecondaryStorage()
                      .getOpensearch()
                      .setIndexPrefix(testContext.getIndexPrefix());
                }
              } else {
                cfg.getData().getSecondaryStorage().getElasticsearch().setUrl(dbUrl);
                if (testContext.getIndexPrefix() != null) {
                  cfg.getData()
                      .getSecondaryStorage()
                      .getElasticsearch()
                      .setIndexPrefix(testContext.getIndexPrefix());
                }
              }
            });
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
