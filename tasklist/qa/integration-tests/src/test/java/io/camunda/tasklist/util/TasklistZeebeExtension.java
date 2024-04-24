/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.zeebe.client.ZeebeClient;
import io.zeebe.containers.ZeebeContainer;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

  private static ContainerPoolManager<ZeebeContainer> zeebeContainerContainerPoolManager;

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
    Testcontainers.exposeHostPorts(getDatabasePort());
    if (environment != null
        && environment.matchesProfiles(TasklistProfileService.IDENTITY_AUTH_PROFILE)) {
      LOGGER.info("Creating Zeebe container with identity enabled");
      zeebeContainer =
          createZeebeContainer()
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
                  IdentityTester.testContext.getInternalIdentityBaseUrl())
              .withEnv(
                  "ZEEBE_BROKER_GATEWAY_MULTITENANCY_ENABLED",
                  String.valueOf(tasklistProperties.getMultiTenancy().isEnabled()));
      zeebeContainer.start();
    } else {
      // for "standard" zeebe configuration, use a container from the pool
      if (zeebeContainerContainerPoolManager == null) {
        zeebeContainerContainerPoolManager =
            new ContainerPoolManager<>(3, this::createZeebeContainer, ZeebeContainer.class).init();
      }
      zeebeContainer = zeebeContainerContainerPoolManager.getContainer();
    }
    this.prefix = zeebeContainer.getEnvMap().get(getZeebeExporterIndexPrefixConfigParameterName());
    LOGGER.info("Using Zeebe container with indexPrefix={}", prefix);
    setZeebeIndexesPrefix(prefix);
    final Integer zeebeRestPort = zeebeContainer.getMappedPort(8080);

    client =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
            .restAddress(
                getURIFromString(
                    String.format("http://%s:%s", zeebeContainer.getExternalHost(), zeebeRestPort)))
            .usePlaintext()
            .defaultRequestTimeout(REQUEST_TIMEOUT)
            .build();
  }

  protected abstract String getZeebeExporterIndexPrefixConfigParameterName();

  private ZeebeContainer createZeebeContainer() {
    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(
            ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME);
    final String indexPrefix = TestUtil.createRandomString(10);
    LOGGER.info(
        "************ Starting Zeebe:{}, indexPrefix={} ************", zeebeVersion, indexPrefix);
    final ZeebeContainer zContainer =
        new ZeebeContainer(DockerImageName.parse("camunda/zeebe").withTag(zeebeVersion))
            .withEnv(getDatabaseEnvironmentVariables(indexPrefix))
            .withEnv("JAVA_OPTS", "-Xss256k -XX:+TieredCompilation -XX:TieredStopAtLevel=1")
            .withEnv("ZEEBE_LOG_LEVEL", "ERROR")
            .withEnv("ATOMIX_LOG_LEVEL", "ERROR")
            .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
            .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "2")
            .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true");
    zContainer.addExposedPort(8080);
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
