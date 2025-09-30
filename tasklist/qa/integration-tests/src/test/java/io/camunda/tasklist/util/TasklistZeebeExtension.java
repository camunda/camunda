/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.client.CamundaClient;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.qa.util.TasklistIndexPrefixHolder;
import io.zeebe.containers.ZeebeContainer;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

public abstract class TasklistZeebeExtension
    implements BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistZeebeExtension.class);

  @Autowired protected TasklistProperties tasklistProperties;
  @Autowired protected SecurityConfiguration securityConfiguration;
  @Autowired protected TasklistIndexPrefixHolder indexPrefixHolder;
  protected ZeebeContainer zeebeContainer;
  protected boolean failed = false;

  private CamundaClient client;

  @Autowired(required = false)
  private Environment environment;

  private String prefix;

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    startZeebe();
  }

  @Override
  public void handleTestExecutionException(
      final ExtensionContext context, final Throwable throwable) throws Throwable {
    failed = true;
    throw throwable;
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    stop();
  }

  private void startZeebe() {
    Testcontainers.exposeHostPorts(getDatabasePort());
    zeebeContainer = createZeebeContainer();
    zeebeContainer.start();
    prefix = zeebeContainer.getEnvMap().get(getZeebeExporterIndexPrefixConfigParameterName());
    LOGGER.info("Using Zeebe container with indexPrefix={}", prefix);
    final Integer zeebeRestPort = zeebeContainer.getMappedPort(8080);

    client =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(false)
            .grpcAddress(zeebeContainer.getGrpcAddress())
            .restAddress(
                getURIFromString(
                    String.format("http://%s:%s", zeebeContainer.getExternalHost(), zeebeRestPort)))
            .defaultRequestTimeout(REQUEST_TIMEOUT)
            .build();
  }

  protected abstract String getZeebeExporterIndexPrefixConfigParameterName();

  private ZeebeContainer createZeebeContainer() {
    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(
            ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME);
    final String zeebeRepo =
        ContainerVersionsUtil.readProperty(
            ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_REPO_PROPERTY_NAME);
    final String indexPrefix = indexPrefixHolder.getIndexPrefix();
    LOGGER.info(
        "************ Starting Zeebe - {}:{}, indexPrefix={} ************",
        zeebeRepo,
        zeebeVersion,
        indexPrefix);
    final ZeebeContainer zContainer =
        new ZeebeContainer(DockerImageName.parse(zeebeRepo).withTag(zeebeVersion))
            .withEnv(getDatabaseEnvironmentVariables(indexPrefix))
            .withEnv("JAVA_OPTS", "-Xss256k -XX:+TieredCompilation -XX:TieredStopAtLevel=1")
            .withEnv("ZEEBE_LOG_LEVEL", "ERROR")
            .withEnv("ATOMIX_LOG_LEVEL", "ERROR")
            .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
            .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "2")
            .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
            .withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true")
            .withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false")
            .withEnv(
                "JAVA_OPTS",
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005");
    zContainer.withLogConsumer(new Slf4jLogConsumer(LOGGER));
    zContainer.addExposedPort(8080);
    zContainer.addExposedPort(5005);
    return zContainer;
  }

  private static URI getURIFromString(final String uri) {
    try {
      return new URI(uri);
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException("Failed to parse URI string", e);
    }
  }

  protected abstract Map<String, String> getDatabaseEnvironmentVariables(String indexPrefix);

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    CompletableFuture.runAsync(() -> zeebeContainer.stop());

    if (client != null) {
      client.close();
      client = null;
    }
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(final String prefix) {
    this.prefix = prefix;
  }

  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  public CamundaClient getClient() {
    return client;
  }

  public void setTasklistProperties(final TasklistProperties tasklistProperties) {
    this.tasklistProperties = tasklistProperties;
  }

  protected abstract int getDatabasePort();
}
