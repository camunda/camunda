/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeContainer;
import java.time.Duration;
import java.time.Instant;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.DockerImageName;

public abstract class TasklistZeebeExtension
    implements BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistZeebeExtension.class);

  @Autowired protected TasklistProperties tasklistProperties;

  protected ZeebeContainer zeebeContainer;

  protected boolean failed = false;

  private ZeebeClient client;

  @Autowired(required = false)
  private Environment environment;

  private String prefix;

  public abstract void refreshIndices(Instant instant);

  @Override
  public void beforeEach(ExtensionContext extensionContext) {
    this.prefix = TestUtil.createRandomString(10);
    setZeebeIndexesPrefix(prefix);
    startZeebe();
  }

  @Override
  public void handleTestExecutionException(ExtensionContext context, Throwable throwable)
      throws Throwable {
    this.failed = true;
    throw throwable;
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) {
    stop();
  }

  protected abstract void setZeebeIndexesPrefix(String prefix);

  private void startZeebe() {
    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(
            ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME);

    LOGGER.info("************ Starting Zeebe:{} ************", zeebeVersion);
    zeebeContainer =
        new ZeebeContainer(DockerImageName.parse("camunda/zeebe").withTag(zeebeVersion));
    Testcontainers.exposeHostPorts(getDatabasePort());
    zeebeContainer
        .withEnv("JAVA_OPTS", "-Xss256k -XX:+TieredCompilation -XX:TieredStopAtLevel=1")
        .withEnv("ZEEBE_LOG_LEVEL", "ERROR")
        .withEnv("ATOMIX_LOG_LEVEL", "ERROR")
        .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
        .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "2");
    setDatabaseEnvironmentVariables(zeebeContainer);
    if (environment != null
        && environment.matchesProfiles(TasklistProfileService.IDENTITY_AUTH_PROFILE)) {
      zeebeContainer
          // .withNetwork(Network.SHARED)
          .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_MODE", "identity")
          .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_TYPE", "keycloak")
          .withEnv(
              "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_ISSUERBACKENDURL",
              IdentityTester.testContext.getInternalKeycloakBaseUrl()
                  + "/auth/realms/camunda-platform")
          .withEnv("ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_AUDIENCE", "zeebe-api")
          .withEnv(
              "ZEEBE_BROKER_GATEWAY_SECURITY_AUTHENTICATION_IDENTITY_BASEURL",
              IdentityTester.testContext.getInternalIdentityBaseUrl())
          .withEnv(
              "ZEEBE_BROKER_GATEWAY_MULTITENANCY_ENABLED",
              String.valueOf(tasklistProperties.getMultiTenancy().isEnabled()));
    }
    zeebeContainer.start();
    client =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .usePlaintext()
            .defaultRequestTimeout(REQUEST_TIMEOUT)
            .build();

    testZeebeIsReady();
    LOGGER.info("************ Zeebe:{} started ************", zeebeVersion);
  }

  protected abstract void setDatabaseEnvironmentVariables(ZeebeContainer zeebeContainer);

  private void testZeebeIsReady() {
    // get topology to check that cluster is available and ready for work
    Topology topology = null;
    while (topology == null) {
      try {
        topology = client.newTopologyRequest().send().join();
      } catch (ClientException ex) {
        ex.printStackTrace();
      }
    }
  }

  /** Stops the broker and destroys the client. Does nothing if not started yet. */
  public void stop() {
    zeebeContainer.stop();

    if (client != null) {
      client.close();
      client = null;
    }
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  public ZeebeClient getClient() {
    return client;
  }

  public void setTasklistProperties(final TasklistProperties tasklistProperties) {
    this.tasklistProperties = tasklistProperties;
  }

  public abstract void setZeebeOsClient(final OpenSearchClient zeebeOsClient);

  public abstract void setZeebeEsClient(final RestHighLevelClient zeebeOsClient);

  protected abstract int getDatabasePort();
}
