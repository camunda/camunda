/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.RetryOperation;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;

import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebePort;
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
import org.springframework.stereotype.Component;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Component
public class TestContainerUtil {

  private static final Logger logger = LoggerFactory.getLogger(TestContainerUtil.class);

  private static final String DOCKER_OPERATE_IMAGE_NAME = "camunda/operate";
  private static final Integer OPERATE_HTTP_PORT = 8080;
  public static final String PROPERTIES_PREFIX = "camunda.operate.";
  private static final String DOCKER_ELASTICSEARCH_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch";
  public static final String ELS_NETWORK_ALIAS = "elasticsearch";
  public static final int ELS_PORT = 9200;

  private Network network;
  private ElasticsearchContainer elsContainer;
  private ZeebeContainer broker;
  private GenericContainer operateContainer;

  public void startElasticsearch(TestContext testContext) {
    logger.info("************ Starting Elasticsearch ************");
    elsContainer = new ElasticsearchContainer(String.format("%s:%s", DOCKER_ELASTICSEARCH_IMAGE_NAME, ElasticsearchClient.class.getPackage().getImplementationVersion()))
        .withNetwork(getNetwork())
        .withEnv("xpack.security.enabled","false")
        .withEnv("path.repo", "~/")
        .withNetworkAliases(ELS_NETWORK_ALIAS)
        .withExposedPorts(ELS_PORT);
    elsContainer
        .setWaitStrategy(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    elsContainer.start();

    testContext.setNetwork(getNetwork());
    testContext.setExternalElsHost(elsContainer.getContainerIpAddress());
    testContext.setExternalElsPort(elsContainer.getMappedPort(ELS_PORT));
    testContext.setInternalElsHost(ELS_NETWORK_ALIAS);
    testContext.setInternalElsPort(ELS_PORT);

    logger.info("************ Elasticsearch started on {}:{} ************", testContext.getExternalElsHost(), testContext.getExternalElsPort());
  }

  public boolean checkElasctisearchHealth(TestContext testContext) {
    try {
      RestHighLevelClient esClient = new RestHighLevelClient(
          RestClient.builder(new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort())));
      return RetryOperation.<Boolean>newBuilder()
          .noOfRetry(5)
          .retryOn(IOException.class, ElasticsearchException.class)
          .delayInterval(3, TimeUnit.SECONDS)
          .retryConsumer(() -> {
            final ClusterHealthResponse clusterHealthResponse = esClient.cluster()
                .health(new ClusterHealthRequest(), RequestOptions.DEFAULT);
            return clusterHealthResponse.getStatus().equals(ClusterHealthStatus.GREEN);
          })
          .build().retry();
    } catch (Exception e) {
      throw new OperateRuntimeException("Couldn't connect to Elasticsearch. Abort.", e);
    }
  }

  public GenericContainer startOperate(String version, TestContext testContext) {
    if (operateContainer == null) {
      logger.info("************ Starting Operate {} ************", version);
      operateContainer = createOperateContainer(version, testContext);
      startOperateContainer(operateContainer, testContext);
      logger.info("************ Operate started  ************");
    } else {
      throw new IllegalStateException("Operate is already started. Call stopOperate first.");
    }
    return operateContainer;
  }

  public GenericContainer createOperateContainer(String dockerImageName, String version, TestContext testContext) {
    operateContainer = new GenericContainer<>(String.format("%s:%s", dockerImageName, version))
        .withExposedPorts(8080)
        .withNetwork(testContext.getNetwork())
        .withCopyFileToContainer(MountableFile.forHostPath(createConfigurationFile(testContext), 0775),
            "/usr/local/operate/config/application.properties")
        .waitingFor(new HttpWaitStrategy()
            .forPort(8080)
            .forPath("/actuator/health")
            .withReadTimeout(Duration.ofSeconds(120)))
        .withStartupTimeout(Duration.ofSeconds(120));
    applyConfiguration(operateContainer, testContext);
    return operateContainer;
  }

  public GenericContainer createOperateContainer(String version, TestContext testContext) {
    return createOperateContainer(DOCKER_OPERATE_IMAGE_NAME, version, testContext);
  }

  public void startOperateContainer(GenericContainer operateContainer, TestContext testContext) {
    operateContainer.start();

    testContext.setExternalOperateHost(operateContainer.getHost());
    testContext.setExternalOperatePort(operateContainer.getMappedPort(OPERATE_HTTP_PORT));
  }

  //for newer versions
  private void applyConfiguration(final GenericContainer<?> operateContainer,
      TestContext testContext) {
    String elsHost = testContext.getInternalElsHost();
    Integer elsPort = testContext.getInternalElsPort();
    operateContainer
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv("SPRING_PROFILES_ACTIVE", "dev");

    String zeebeContactPoint = testContext.getInternalZeebeContactPoint();
    if (zeebeContactPoint != null) {
      operateContainer.withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebeContactPoint);
    }
    if (testContext.getZeebeIndexPrefix() != null) {
      operateContainer.withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX", testContext.getZeebeIndexPrefix());
    }

  }

  protected Path createConfigurationFile(TestContext testContext) {
    try {
      Properties properties = getOperateElsProperties(testContext.getInternalElsHost(),
          testContext.getInternalElsPort(), testContext.getInternalZeebeContactPoint(),
          testContext.getZeebeIndexPrefix());
      final Path tempFile = Files.createTempFile(getClass().getPackage().getName(), ".tmp");
      properties.store(new FileWriter(tempFile.toFile()), null);
      return tempFile;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  //for older versions
  protected Properties getOperateElsProperties(String elsHost, Integer elsPort, String zeebeContactPoint,
      String zeebeIndexPrefix) {
    Properties properties = new Properties();
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.port", String.valueOf(elsPort));
    properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.port", String.valueOf(elsPort));
    if (zeebeContactPoint != null) {
      properties.setProperty(PROPERTIES_PREFIX + "zeebe.brokerContactPoint", zeebeContactPoint);
      properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.prefix", zeebeIndexPrefix);
    }
    properties.setProperty(PROPERTIES_PREFIX + "archiver.waitPeriodBeforeArchiving", "2m");
    return properties;
  }

  public ZeebeContainer startZeebe(final String version, TestContext testContext) {
    if (broker == null) {
      logger.info("************ Starting Zeebe {} ************", version);
      broker = new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + version))
          .withNetwork(testContext.getNetwork())
          .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
      if (testContext.getZeebeDataFolder() != null) {
        broker.withFileSystemBind(testContext.getZeebeDataFolder().getPath(), "/usr/local/zeebe/data");
      }
      broker.setWaitStrategy(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
      addConfig(broker, testContext);
      broker.start();
      logger.info("************ Zeebe started  ************");

      testContext.setInternalZeebeContactPoint(broker.getInternalAddress(ZeebePort.GATEWAY.getPort()));
      testContext.setExternalZeebeContactPoint(broker.getExternalAddress(ZeebePort.GATEWAY.getPort()));
    } else {
      throw new IllegalStateException("Broker is already started. Call stopZeebe first.");
    }
    return broker;
  }

  protected void addConfig(ZeebeContainer zeebeBroker, TestContext testContext) {
    zeebeBroker.withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME", "io.camunda.zeebe.exporter.ElasticsearchExporter")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", "http://elasticsearch:9200");
    if (testContext.getZeebeIndexPrefix() != null) {
      zeebeBroker.withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", testContext.getZeebeIndexPrefix());
    }
  }

  public void stopZeebeAndOperate(TestContext testContext) {
    stopZeebe(testContext);
    stopOperate(testContext);
  }

  protected void stopZeebe(TestContext testContext) {
    if (broker != null) {
      broker.shutdownGracefully(Duration.ofSeconds(3));
      broker = null;
    }
    testContext.setInternalZeebeContactPoint(null);
    testContext.setExternalZeebeContactPoint(null);
  }

  protected void stopOperate(TestContext testContext) {
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

  private void closeNetwork(){
    if (network != null) {
      network.close();
      network = null;
    }
  }

}
