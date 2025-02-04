/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.migration;

import io.camunda.application.commons.migration.PrefixMigrationHelper;
import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class PrefixMigrationIT {

  public static final String COOKIE_JSESSIONID = "OPERATE-SESSION";
  private static final int SERVER_PORT = 8080;
  private static final int MANAGEMENT_PORT = 9600;
  private static final int GATEWAY_GRPC_PORT = 26500;
  private static final String ELASTIC_ALIAS = "elasticsearch";
  private static final Network NETWORK = Network.newNetwork();
  private static final String LOGIN_ENDPOINT = "/api/login?username=%s&password=%s";
  private static final String LOGIN_USERNAME = "demo";
  private static final String LOGIN_PASSWORD = "demo";
  private static final String OLD_OPERATE_PREFIX = "operate-dev";
  private static final String OLD_TASKLIST_PREFIX = "tasklist-dev";
  private static final String NEW_PREFIX = "";

  @Container
  private final ElasticsearchContainer esContainer =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withNetwork(NETWORK)
          .withNetworkAliases(ELASTIC_ALIAS)
          .withStartupTimeout(Duration.ofMinutes(5)); // can be slow in CI

  private GenericContainer<?> createCamundaContainer(
      final String tag,
      final String operatePrefix,
      final String tasklistPrefix,
      final String newPrefix) {
    final var esUrl = String.format("http://%s:%d", ELASTIC_ALIAS, 9200);

    final var container =
        new GenericContainer<>(DockerImageName.parse("camunda/camunda:" + tag))
            .withNetwork(NETWORK)
            .withNetworkAliases("camunda")
            .withExposedPorts(SERVER_PORT, MANAGEMENT_PORT, GATEWAY_GRPC_PORT)
            .withEnv("CAMUNDA_OPERATE_CSRFPREVENTIONENABLED", "false")
            .withEnv("CAMUNDA_TASKLIST_CSRFPREVENTIONENABLED", "false")
            .withEnv("CAMUNDA_DATABASE_URL", esUrl)
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_INDEX_PREFIX", operatePrefix)
            .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_INDEX_PREFIX", tasklistPrefix)
            .withEnv(
                "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
                "io.camunda.zeebe.exporter.ElasticsearchExporter")
            .withEnv("CAMUNDA_OPERATE_ELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_OPERATE_ZEEBEELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", esUrl)
            .withEnv("CAMUNDA_TASKLIST_ZEEBEELASTICSEARCH_URL", esUrl)
            .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL", esUrl);

    if ("8.6.4".equals(tag)) {
      container.waitingFor(
          new HttpWaitStrategy()
              .forPort(MANAGEMENT_PORT)
              .forPath("/actuator/health")
              .withReadTimeout(Duration.ofSeconds(30)));
    }

    if (tag.contains("8.7.0")) {
      container
          .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL", esUrl)
          .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE", "1")
          .withEnv(
              "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME",
              "io.camunda.exporter.CamundaExporter")
          .withEnv("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_PREFIX", newPrefix);
    }

    return container;
  }

  private ZeebeClient create86Client(final GenericContainer<?> container86) {
    return ZeebeClient.newClientBuilder()
        .grpcAddress(URI.create("http://localhost:" + container86.getMappedPort(GATEWAY_GRPC_PORT)))
        .restAddress(URI.create("http://localhost:" + container86.getMappedPort(SERVER_PORT)))
        .usePlaintext()
        .build();
  }

  private void prefixMigration() throws IOException {
    final var operate = new OperateProperties();
    final var tasklist = new TasklistProperties();
    final var connect = new ConnectConfiguration();

    operate.getElasticsearch().setIndexPrefix(OLD_OPERATE_PREFIX);
    tasklist.getElasticsearch().setIndexPrefix(OLD_TASKLIST_PREFIX);
    connect.setUrl("http://localhost:" + esContainer.getMappedPort(9200));
    PrefixMigrationHelper.runPrefixMigration(operate, tasklist, connect);
  }

  private CamundaClient create87Client(final GenericContainer<?> container87) throws IOException {
    final BasicCookieStore cookieStore = new BasicCookieStore();
    final var httpClient = HttpClientBuilder.create().setDefaultCookieStore(cookieStore).build();

    final var restApiAddress = "http://localhost:" + container87.getMappedPort(SERVER_PORT);
    httpClient.execute(
        new HttpPost(
            String.format(restApiAddress + LOGIN_ENDPOINT, LOGIN_USERNAME, LOGIN_PASSWORD)),
        response -> {
          if (response.getCode() != 204) {
            throw new RuntimeException(
                String.format(
                    "Failed to login. [code: %d, message: %s]",
                    response.getCode(), response.getEntity().toString()));
          }
          return null;
        });

    return CamundaClient.newClientBuilder()
        .grpcAddress(URI.create("http://localhost:" + container87.getMappedPort(GATEWAY_GRPC_PORT)))
        .restAddress(URI.create("http://localhost:" + container87.getMappedPort(SERVER_PORT)))
        .usePlaintext()
        .credentialsProvider(
            new CredentialsProvider() {
              @Override
              public void applyCredentials(final CredentialsApplier applier) {
                applier.put(
                    "Cookie",
                    String.format(
                        COOKIE_JSESSIONID + "=%s", cookieStore.getCookies().getFirst().getValue()));
              }

              @Override
              public boolean shouldRetryRequest(final StatusCode statusCode) {
                return false;
              }
            })
        .build();
  }

  @Test
  void shouldReindexDocumentsDuringPrefixMigration() throws IOException, InterruptedException {
    // given
    final var camunda86 =
        createCamundaContainer("8.6.4", OLD_OPERATE_PREFIX, OLD_TASKLIST_PREFIX, "");
    camunda86.start();

    final var client86 = create86Client(camunda86);

    final var event =
        client86
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/incident_process_v1.bpmn")
            .send()
            .join();

    // Wait for operate importer to write documents to indices,
    Thread.sleep(5000);

    camunda86.stop();

    // when
    prefixMigration();

    // then
    final var camunda87 =
        createCamundaContainer("8.7.0-alpha3", NEW_PREFIX, NEW_PREFIX, NEW_PREFIX);
    camunda87.start();

    final var client87 = create87Client(camunda87);

    final var processDefinitions = client87.newProcessDefinitionQuery().send().join();
    Assertions.assertThat(processDefinitions.items().size()).isEqualTo(1);
    Assertions.assertThat(processDefinitions.items().getFirst().getProcessDefinitionKey())
        .isEqualTo(event.getProcesses().getFirst().getProcessDefinitionKey());
  }
}
