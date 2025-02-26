/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.time.Duration;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@ZeebeIntegration
@Testcontainers
final class StandaloneSchemaManagerTest {

  public static final String ADMIN_USER = "camunda-admin";
  public static final String ADMIN_PASSWORD = "admin123";
  public static final String ADMIN_ROLE = "superuser";

  public static final String APP_USER = "camunda-app";
  public static final String APP_PASSWORD = "app123";
  public static final String APP_ROLE = "camunda_app_role";
  public static final String APP_ROLE_DEFINITION =
      // language=yaml
      """
      camunda_app_role:
        indices:
          - names: ['zeebe-*', 'operate-*', 'tasklist-*']
            privileges: ['manage']
      """;

  @TestZeebe(autoStart = false)
  final TestStandaloneSchemaManager schemaManager =
      new TestStandaloneSchemaManager()
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.className",
              "io.camunda.zeebe.exporter.ElasticsearchExporter")
          .withProperty("zeebe.broker.exporters.elasticsearch.args.index.createTemplate", "true")
          .withProperty("zeebe.broker.exporters.elasticsearch.args.retention.enabled", "true")
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.args.authentication.username", ADMIN_USER)
          .withProperty(
              "zeebe.broker.exporters.elasticsearch.args.authentication.password", ADMIN_PASSWORD)
          .withProperty("camunda.operate.elasticsearch.username", ADMIN_USER)
          .withProperty("camunda.operate.elasticsearch.password", ADMIN_PASSWORD)
          .withProperty("camunda.operate.elasticsearch.healthCheckEnabled", "false")
          .withProperty("camunda.operate.archiver.ilmEnabled", "true")
          .withProperty("camunda.tasklist.database", "elasticsearch")
          .withProperty("camunda.tasklist.elasticsearch.username", ADMIN_USER)
          .withProperty("camunda.tasklist.elasticsearch.password", ADMIN_PASSWORD)
          .withProperty("camunda.tasklist.elasticsearch.healthCheckEnabled", "false")
          .withProperty("camunda.tasklist.archiver.ilmEnabled", "true");

  @Container
  private final ElasticsearchContainer es =
      new ElasticsearchContainer(
              DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch")
                  .withTag(RestClient.class.getPackage().getImplementationVersion()))
          // Enable security features
          .withEnv("xpack.security.enabled", "true")
          // Ensure a fast and reliable startup
          .withStartupTimeout(Duration.ofMinutes(5))
          .withStartupAttempts(3)
          .withEnv("xpack.security.enabled", "true")
          .withEnv("xpack.watcher.enabled", "false")
          .withEnv("xpack.ml.enabled", "false")
          .withCopyToContainer(
              Transferable.of(APP_ROLE_DEFINITION), "/usr/share/elasticsearch/config/roles.yml")
          .withClasspathResourceMapping(
              "elasticsearch-fast-startup.options",
              "/usr/share/elasticsearch/config/jvm.options.d/elasticsearch-fast-startup.options",
              BindMode.READ_ONLY);

  @BeforeEach
  void setup() throws IOException, InterruptedException {
    // setup ES users
    es.execInContainer("elasticsearch-users", "useradd", APP_USER, "-p", APP_PASSWORD);
    es.execInContainer("elasticsearch-users", "useradd", ADMIN_USER, "-p", ADMIN_PASSWORD);
    es.execInContainer("elasticsearch-users", "roles", ADMIN_USER, "-a", ADMIN_ROLE);
    es.execInContainer("elasticsearch-users", "roles", APP_USER, "-a", APP_ROLE);
    // Connect to ES
    schemaManager.withProperty(
        "zeebe.broker.exporters.elasticsearch.args.url", "http://" + es.getHttpHostAddress());
    schemaManager.withProperty(
        "camunda.operate.elasticsearch.url", "http://" + es.getHttpHostAddress());
    schemaManager.withProperty(
        "camunda.tasklist.elasticsearch.url", "http://" + es.getHttpHostAddress());
  }

  @Test
  void canRunSchemaManager() {
    schemaManager.start();
  }
}
