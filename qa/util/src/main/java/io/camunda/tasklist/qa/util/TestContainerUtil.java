/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.util;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebePort;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import javax.annotation.PreDestroy;
import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Component
@Configuration
@EnableRetry
public class TestContainerUtil {

  public static final String ELS_NETWORK_ALIAS = "elasticsearch";
  public static final int ELS_PORT = 9200;
  public static final String PROPERTIES_PREFIX = "camunda.tasklist.";
  private static final Logger LOGGER = LoggerFactory.getLogger(TestContainerUtil.class);
  private static final String DOCKER_TASKLIST_IMAGE_NAME = "camunda/tasklist";
  private static final Integer TASKLIST_HTTP_PORT = 8080;
  private static final String DOCKER_ELASTICSEARCH_IMAGE_NAME =
      "docker.elastic.co/elasticsearch/elasticsearch";
  private Network network;
  private ElasticsearchContainer elsContainer;
  private ZeebeContainer broker;
  private GenericContainer tasklistContainer;

  public void startElasticsearch(TestContext testContext) {
    LOGGER.info("************ Starting Elasticsearch ************");
    elsContainer =
        new ElasticsearchContainer(
                String.format(
                    "%s:%s",
                    DOCKER_ELASTICSEARCH_IMAGE_NAME,
                    ElasticsearchClient.class.getPackage().getImplementationVersion()))
            .withNetwork(getNetwork())
            .withEnv("xpack.security.enabled", "false")
            .withEnv("path.repo", "~/")
            .withNetworkAliases(ELS_NETWORK_ALIAS)
            .withExposedPorts(ELS_PORT);
    elsContainer.setWaitStrategy(
        new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    elsContainer.start();

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

  @Retryable(
      value = TasklistRuntimeException.class,
      maxAttempts = 5,
      backoff = @Backoff(delay = 3000))
  public boolean checkElasctisearchHealth(TestContext testContext) {
    try {
      final RestHighLevelClient esClient =
          new RestHighLevelClient(
              RestClient.builder(
                  new HttpHost(
                      testContext.getExternalElsHost(), testContext.getExternalElsPort())));
      final ClusterHealthResponse clusterHealthResponse =
          esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
      return clusterHealthResponse.getStatus().equals(ClusterHealthStatus.GREEN);
    } catch (IOException | ElasticsearchException ex) {
      throw new TasklistRuntimeException();
    }
  }

  public GenericContainer startTasklist(String version, TestContext testContext) {
    if (tasklistContainer == null) {
      LOGGER.info("************ Starting Tasklist {} ************", version);
      tasklistContainer = createTasklistContainer(version, testContext);
      startTasklistContainer(tasklistContainer, testContext);
      LOGGER.info("************ Tasklist started  ************");
    } else {
      throw new IllegalStateException("Tasklist is already started. Call stopTasklist first.");
    }
    return tasklistContainer;
  }

  public GenericContainer createTasklistContainer(
      String dockerImageName, String version, TestContext testContext) {
    tasklistContainer =
        new GenericContainer<>(String.format("%s:%s", dockerImageName, version))
            .withExposedPorts(8080)
            .withNetwork(testContext.getNetwork())
            .withCopyFileToContainer(
                MountableFile.forHostPath(createConfigurationFile(testContext), 0775),
                "/usr/local/tasklist/config/application.properties")
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(8080)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)))
            .withStartupTimeout(Duration.ofSeconds(120));
    applyConfiguration(tasklistContainer, testContext);
    return tasklistContainer;
  }

  public GenericContainer createTasklistContainer(String version, TestContext testContext) {
    return createTasklistContainer(DOCKER_TASKLIST_IMAGE_NAME, version, testContext);
  }

  public void startTasklistContainer(GenericContainer tasklistContainer, TestContext testContext) {
    tasklistContainer.start();

    testContext.setExternalTasklistHost(tasklistContainer.getHost());
    testContext.setExternalTasklistPort(tasklistContainer.getMappedPort(TASKLIST_HTTP_PORT));
  }

  // for newer versions
  private void applyConfiguration(
      final GenericContainer<?> tasklistContainer, TestContext testContext) {
    final String elsHost = testContext.getInternalElsHost();
    final Integer elsPort = testContext.getInternalElsPort();
    tasklistContainer
        .withEnv(
            "CAMUNDA_TASKLIST_ELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv(
            "CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL",
            String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_UHOST", elsHost)
        .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PORT", String.valueOf(elsPort));

    final String zeebeContactPoint = testContext.getInternalZeebeContactPoint();
    if (zeebeContactPoint != null) {
      tasklistContainer.withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebeContactPoint);
    }
    if (testContext.getZeebeIndexPrefix() != null) {
      tasklistContainer.withEnv(
          "CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX", testContext.getZeebeIndexPrefix());
    }
  }

  protected Path createConfigurationFile(TestContext testContext) {
    try {
      final Properties properties =
          getTasklistElsProperties(
              testContext.getInternalElsHost(),
              testContext.getInternalElsPort(),
              testContext.getInternalZeebeContactPoint(),
              testContext.getZeebeIndexPrefix());
      final Path tempFile = Files.createTempFile(getClass().getPackage().getName(), ".tmp");
      properties.store(new FileWriter(tempFile.toFile()), null);
      return tempFile;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // for older versions
  protected Properties getTasklistElsProperties(
      String elsHost, Integer elsPort, String zeebeContactPoint, String zeebeIndexPrefix) {
    final Properties properties = new Properties();
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.port", String.valueOf(elsPort));
    properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.port", String.valueOf(elsPort));
    if (zeebeContactPoint != null) {
      properties.setProperty(PROPERTIES_PREFIX + "zeebe.brokerContactPoint", zeebeContactPoint);
    }
    if (zeebeIndexPrefix != null) {
      properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.prefix", zeebeIndexPrefix);
    }
    properties.setProperty(PROPERTIES_PREFIX + "archiver.waitPeriodBeforeArchiving", "2m");
    return properties;
  }

  public ZeebeContainer startZeebe(final String version, TestContext testContext) {
    if (broker == null) {
      LOGGER.info("************ Starting Zeebe {} ************", version);
      broker =
          new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + version))
              .withNetwork(testContext.getNetwork())
              .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
      if (testContext.getZeebeDataFolder() != null) {
        broker.withFileSystemBind(
            testContext.getZeebeDataFolder().getPath(), "/usr/local/zeebe/data");
      }
      broker.setWaitStrategy(
          new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
      addConfig(broker, testContext);
      broker.start();
      LOGGER.info("************ Zeebe started  ************");

      testContext.setInternalZeebeContactPoint(
          broker.getInternalAddress(ZeebePort.GATEWAY.getPort()));
      testContext.setExternalZeebeContactPoint(
          broker.getExternalAddress(ZeebePort.GATEWAY.getPort()));
    } else {
      throw new IllegalStateException("Broker is already started. Call stopZeebe first.");
    }
    return broker;
  }

  protected void addConfig(ZeebeContainer zeebeBroker, TestContext testContext) {
    zeebeBroker
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://elasticsearch:9200");
    if (testContext.getZeebeIndexPrefix() != null) {
      zeebeBroker.withEnv(
          "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX",
          testContext.getZeebeIndexPrefix());
    }
  }

  public void stopZeebeAndTasklist(TestContext testContext) {
    stopZeebe(testContext);
    stopTasklist(testContext);
  }

  protected void stopZeebe(TestContext testContext) {
    if (broker != null) {
      broker.shutdownGracefully(Duration.ofSeconds(3));
      broker = null;
    }
    testContext.setInternalZeebeContactPoint(null);
    testContext.setExternalZeebeContactPoint(null);
  }

  protected void stopTasklist(TestContext testContext) {
    if (tasklistContainer != null) {
      tasklistContainer.close();
      tasklistContainer = null;
    }
    testContext.setExternalTasklistHost(null);
    testContext.setExternalTasklistPort(null);
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
