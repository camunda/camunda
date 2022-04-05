/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util.migration;

import io.zeebe.containers.ZeebeContainer;
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

  private static final Logger logger = LoggerFactory.getLogger(AbstractTestFixture.class);

  private static final String ZEEBE_CFG_YAML_FILE = "/zeebe-config/zeebe.cfg.yaml";
  private static final String DOCKER_OPERATE_IMAGE_NAME = "camunda/operate";
  private static final Integer OPERATE_HTTP_PORT = 8080;
  public static final String PROPERTIES_PREFIX = "camunda.operate.";
  private static final String ZEEBE_PREFIX = "migration-test";

  protected ZeebeContainer broker;
  protected GenericContainer<?> operateContainer;

  protected TestContext testContext;

  @Override
  public void setup(TestContext testContext) {
    this.testContext = testContext;
  }

  protected void startZeebeAndOperate() {
    startZeebe(getVersion());
    startOperate(getVersion());
  }

  protected void startOperate(String version) {
    logger.info("************ Starting Operate {} ************", version);
    operateContainer = new GenericContainer<>(String.format("%s:%s", DOCKER_OPERATE_IMAGE_NAME, version))
        .withExposedPorts(8080)
        .withNetwork(testContext.getNetwork())
        .withCopyFileToContainer(MountableFile.forHostPath(createConfigurationFile()), "/usr/local/operate/config/application.properties")
        .waitingFor(new HttpWaitStrategy()
            .forPort(8080)
            .forPath("/actuator/health")
            .withReadTimeout(Duration.ofSeconds(120)));
    applyConfiguration(operateContainer, testContext.getInternalElsHost(),
        testContext.getInternalElsPort(), testContext.getInternalZeebeContactPoint());
    operateContainer.start();

    testContext.setExternalOperateHost(operateContainer.getContainerIpAddress());
    testContext.setExternalOperatePort(operateContainer.getMappedPort(OPERATE_HTTP_PORT));
    logger.info("************ Operate started  ************");
  }

  //for newer versions
  private void applyConfiguration(final GenericContainer<?> operateContainer,
      String elsHost, Integer elsPort, String zeebeContactPoint) {
    operateContainer
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_HOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_PORT", String.valueOf(elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", String.format("http://%s:%s", elsHost, elsPort))
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_UHOST", elsHost)
        .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PORT", String.valueOf(elsPort));

    if (zeebeContactPoint != null) {
      operateContainer.withEnv("CAMUNDA_OPERATE_ZEEBE_GATEWAYADDRESS", zeebeContactPoint)
          .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_PREFIX", ZEEBE_PREFIX);
    }

  }

  protected Path createConfigurationFile() {
    try {
      Properties properties = getOperateElsProperties(testContext.getInternalElsHost(), testContext.getInternalElsPort(), testContext.getInternalZeebeContactPoint());
      final Path tempFile = Files.createTempFile(getClass().getPackage().getName(), ".tmp");
      properties.store(new FileWriter(tempFile.toFile()), null);
      return tempFile;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  //for older versions
  protected Properties getOperateElsProperties(String elsHost, Integer elsPort, String zeebeContactPoint) {
    Properties properties = new Properties();
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
    logger.info("************ Starting Zeebe {} ************", version);
    broker = new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + version))
        .withFileSystemBind(testContext.getZeebeDataFolder().getPath(), "/usr/local/zeebe/data")
        .withNetwork(testContext.getNetwork())
        .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
    broker.setWaitStrategy(new HostPortWaitStrategy().withStartupTimeout(Duration.ofSeconds(240L)));
    addConfig(broker);
    broker.start();
    logger.info("************ Zeebe started  ************");

    testContext.setInternalZeebeContactPoint(broker.getInternalAddress(ZeebePort.GATEWAY.getPort()));
    testContext.setExternalZeebeContactPoint(broker.getExternalAddress(ZeebePort.GATEWAY.getPort()));
  }

  protected void addConfig(ZeebeContainer zeebeBroker) {
    zeebeBroker.withCopyFileToContainer(MountableFile.forClasspathResource(ZEEBE_CFG_YAML_FILE), "/usr/local/zeebe/config/application.yaml");
  }

  protected void stopZeebeAndOperate() {
    stopZeebe();
    stopOperate();
  }

  protected void stopZeebe() {
    if (broker != null) {
      broker.shutdownGracefully(Duration.ofSeconds(3));
      broker = null;
    }
    testContext.setInternalZeebeContactPoint(null);
    testContext.setExternalZeebeContactPoint(null);
  }

  protected void stopOperate() {
    if (operateContainer != null) {
      operateContainer.close();
      operateContainer = null;
    }
    testContext.setExternalOperateHost(null);
    testContext.setExternalOperatePort(null);
  }


}
