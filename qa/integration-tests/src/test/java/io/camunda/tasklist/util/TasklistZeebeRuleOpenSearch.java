/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.runner.Description;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.DockerImageName;

public class TasklistZeebeRuleOpenSearch extends TasklistZeebeRule {

  public static final String YYYY_MM_DD = "uuuu-MM-dd";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistZeebeRuleOpenSearch.class);
  @Autowired public TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("zeebeOsClient")
  protected OpenSearchClient zeebeOsClient;

  protected ZeebeContainer zeebeContainer;
  private ZeebeClient client;

  private String prefix;
  private boolean failed = false;

  public void refreshIndices(Instant instant) {
    try {
      final String date =
          DateTimeFormatter.ofPattern(YYYY_MM_DD).withZone(ZoneId.systemDefault()).format(instant);
      zeebeOsClient.indices().refresh(r -> r.index(prefix + "*" + date));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void failed(Throwable e, Description description) {
    this.failed = true;
  }

  @Override
  public void starting(Description description) {
    this.prefix = TestUtil.createRandomString(10);
    tasklistProperties.getZeebeOpenSearch().setPrefix(prefix);

    startZeebe();
  }

  private void startZeebe() {
    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(
            ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME);

    LOGGER.info("************ Starting Zeebe:{} ************", zeebeVersion);
    zeebeContainer =
        new ZeebeContainer(DockerImageName.parse("camunda/zeebe").withTag(zeebeVersion));
    Testcontainers.exposeHostPorts(9205);
    zeebeContainer
        .withEnv("JAVA_OPTS", "-Xss256k -XX:+TieredCompilation -XX:TieredStopAtLevel=1")
        .withEnv("ZEEBE_LOG_LEVEL", "ERROR")
        .withEnv("ATOMIX_LOG_LEVEL", "ERROR")
        .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
        .withEnv("ZEEBE_BROKER_CLUSTER_PARTITIONSCOUNT", "2")
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL",
            "http://host.testcontainers.internal:9205")
        .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULK_DELAY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_INDEX_PREFIX", prefix)
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.opensearch.OpensearchExporter")
        .withEnv(
            "ZEEBE_BROKER_GATEWAY_MULTITENANCY_ENABLED",
            String.valueOf(tasklistProperties.getMultiTenancy().isEnabled()));
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

  @Override
  public void finished(Description description) {
    stop();
    if (!failed) {
      TestUtil.removeAllIndices(zeebeOsClient, prefix);
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

  @Override
  public void setZeebeEsClient(RestHighLevelClient zeebeOsClient) {}

  public void setZeebeOsClient(final OpenSearchClient zeebeOsClient) {
    this.zeebeOsClient = zeebeOsClient;
  }
}
