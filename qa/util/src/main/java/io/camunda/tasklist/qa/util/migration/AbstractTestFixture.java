/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.qa.util.migration;

import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebePort;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public abstract class AbstractTestFixture implements TestFixture {

  public static final String PROPERTIES_PREFIX = "camunda.tasklist.";
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTestFixture.class);
  private static final String ZEEBE_CFG_YAML_FILE = "/zeebe-config/zeebe.cfg.yaml";
  private static final String DOCKER_TASKLIST_IMAGE_NAME = "camunda/tasklist";
  private static final Integer TASKLIST_HTTP_PORT = 8080;
  private static final String ZEEBE_PREFIX = "migration-test";

  protected ZeebeBrokerContainer broker;
  protected GenericContainer<?> tasklistContainer;

  protected TestContext testContext;

  @Override
  public void setup(TestContext testContext) {
    this.testContext = testContext;
  }

  protected void startZeebeAndTasklist() {
    startZeebe(getVersion());
    startTasklist(getVersion());
  }

  protected void startTasklist(String version) {
    LOGGER.info("************ Starting Tasklist {} ************", version);
    tasklistContainer =
        new GenericContainer<>(String.format("%s:%s", DOCKER_TASKLIST_IMAGE_NAME, version))
            .withExposedPorts(8080)
            .withNetwork(testContext.getNetwork())
            .withCopyFileToContainer(
                MountableFile.forHostPath(createConfigurationFile()),
                "/usr/local/tasklist/config/application.properties")
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(8080)
                    .forPath("/actuator/health")
                    .withReadTimeout(Duration.ofSeconds(120)));
    applyConfiguration(
        tasklistContainer,
        testContext.getInternalElsHost(),
        testContext.getInternalElsPort(),
        testContext.getInternalZeebeContactPoint());
    tasklistContainer.start();

    testContext.setExternalTasklistHost(tasklistContainer.getContainerIpAddress());
    testContext.setExternalTasklistPort(tasklistContainer.getMappedPort(TASKLIST_HTTP_PORT));
    LOGGER.info("************ Tasklist started  ************");
  }

  // for newer versions
  private void applyConfiguration(
      final GenericContainer<?> tasklistContainer,
      String elsHost,
      Integer elsPort,
      String zeebeContactPoint) {
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

    if (zeebeContactPoint != null) {
      tasklistContainer
          .withEnv("CAMUNDA_TASKLIST_ZEEBE_GATEWAYADDRESS", zeebeContactPoint)
          .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_PREFIX", ZEEBE_PREFIX);
    }
  }

  protected Path createConfigurationFile() {
    try {
      final Properties properties =
          getTasklistElsProperties(
              testContext.getInternalElsHost(),
              testContext.getInternalElsPort(),
              testContext.getInternalZeebeContactPoint());
      final Path tempFile = Files.createTempFile(getClass().getPackage().getName(), ".tmp");
      properties.store(new FileWriter(tempFile.toFile()), null);
      return tempFile;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // for older versions
  protected Properties getTasklistElsProperties(
      String elsHost, Integer elsPort, String zeebeContactPoint) {
    final Properties properties = new Properties();
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "elasticsearch.port", String.valueOf(elsPort));
    properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.host", elsHost);
    properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.port", String.valueOf(elsPort));
    if (zeebeContactPoint != null) {
      properties.setProperty(PROPERTIES_PREFIX + "zeebe.brokerContactPoint", zeebeContactPoint);
      properties.setProperty(PROPERTIES_PREFIX + "zeebeElasticsearch.prefix", ZEEBE_PREFIX);
    }
    properties.setProperty(PROPERTIES_PREFIX + "archiver.waitPeriodBeforeArchiving", "2m");
    return properties;
  }

  protected void startZeebe(final String version) {
    LOGGER.info("************ Starting Zeebe {} ************", version);
    broker =
        new ZeebeBrokerContainer(DockerImageName.parse("camunda/zeebe:" + version))
            .withFileSystemBind(testContext.getZeebeDataFolder().getPath(), "/usr/local/zeebe/data")
            .withNetwork(testContext.getNetwork())
            .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
    broker.setWaitStrategy(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    addConfig(broker);
    broker.start();
    LOGGER.info("************ Zeebe started  ************");

    testContext.setInternalZeebeContactPoint(
        broker.getInternalAddress(ZeebePort.GATEWAY.getPort()));
    testContext.setExternalZeebeContactPoint(
        broker.getExternalAddress(ZeebePort.GATEWAY.getPort()));
  }

  protected void addConfig(ZeebeBrokerContainer zeebeBroker) {
    zeebeBroker.withCopyFileToContainer(
        MountableFile.forClasspathResource(ZEEBE_CFG_YAML_FILE),
        "/usr/local/zeebe/config/application.yaml");
  }

  protected void stopZeebeAndTasklist() {
    stopZeebe();
    stopTasklist();
  }

  protected void stopZeebe() {
    if (broker != null) {
      broker.shutdownGracefully(Duration.ofSeconds(3));
      broker = null;
    }
    testContext.setInternalZeebeContactPoint(null);
    testContext.setExternalZeebeContactPoint(null);
  }

  protected void stopTasklist() {
    if (tasklistContainer != null) {
      tasklistContainer.close();
      tasklistContainer = null;
    }
    testContext.setExternalTasklistHost(null);
    testContext.setExternalTasklistPort(null);
  }
}
