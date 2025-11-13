/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema.strategy;

import static io.camunda.webapps.schema.SupportedVersions.SUPPORTED_OPENSEARCH_VERSION;

import io.camunda.application.Profile;
import io.camunda.exporter.CamundaExporter;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterConfiguration;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporterSchemaManager;
import java.time.Duration;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public final class OpenSearchBackendStrategy implements SearchBackendStrategy {

  private OpensearchContainer<?> container;
  private OpenSearchClient adminClient;
  private String url;

  @Override
  public void startContainer() throws Exception {
    if (container != null) {
      throw new IllegalStateException("Container is already started");
    }
    container =
        new OpensearchContainer<>(
                DockerImageName.parse("opensearchproject/opensearch")
                    .withTag(SUPPORTED_OPENSEARCH_VERSION))
            .withStartupTimeout(Duration.ofMinutes(5))
            .withStartupAttempts(3);

    container.start();
    url = container.getHttpHostAddress();
  }

  @Override
  public GenericContainer<?> getContainer() {
    return container;
  }

  @Override
  public void createAdminClient() throws Exception {
    final var cfg = new ConnectConfiguration();
    cfg.setUrl(url);
    cfg.setUsername(container.getUsername());
    cfg.setPassword(container.getPassword());
    adminClient = new OpensearchConnector(cfg).createClient();
  }

  @Override
  public void createSchema() throws Exception {
    final var adminCfg = exporterAdminConfig();
    final var schemaManager = new OpensearchExporterSchemaManager(adminCfg);
    schemaManager.createSchema(adminClient.info().version().number());
  }

  @Override
  public void configureStandaloneSchemaManager(final TestStandaloneSchemaManager schemaManager) {
    schemaManager
        .withProperty(
            "zeebe.broker.exporters.opensearch.class-name", OpensearchExporter.class.getName())
        .withProperty("zeebe.broker.exporters.opensearch.args.url", url)
        .withProperty("camunda.data.secondary-storage.type", "opensearch")
        .withProperty("camunda.data.secondary-storage.opensearch.url", url)
        .withProperty("camunda.data.secondary-storage.opensearch.username", container.getUsername())
        .withProperty(
            "camunda.data.secondary-storage.opensearch.password", container.getPassword());
  }

  @Override
  public void configureCamundaApplication(final TestCamundaApplication camunda) {
    camunda
        .withAdditionalProfile(Profile.CONSOLIDATED_AUTH)
        .withUnauthenticatedAccess()
        .withProperty("camunda.database.url", url)
        .withProperty("camunda.database.type", "opensearch")
        .withProperty("camunda.data.secondary-storage.type", "opensearch")
        .withProperty("camunda.data.secondary-storage.opensearch.url", url)
        .withExporter(
            CamundaExporter.class.getSimpleName(),
            cfg -> {
              cfg.setClassName(CamundaExporter.class.getName());
              cfg.setArgs(
                  Map.of(
                      "connect",
                      Map.of("url", url, "type", "opensearch"),
                      "history",
                      Map.of("waitPeriodBeforeArchiving", "1s"),
                      "createSchema",
                      false));
            })
        .withExporter(
            OpensearchExporter.class.getSimpleName(),
            cfg -> {
              cfg.setClassName(OpensearchExporter.class.getName());
              cfg.setArgs(Map.of("url", url, "index", Map.of("createTemplate", true)));
            });
  }

  @Override
  public long countDocuments(final String indexPattern) throws Exception {
    return adminClient.count(c -> c.index(indexPattern)).count();
  }

  @Override
  public long countTemplates(final String namePattern) throws Exception {
    return adminClient.indices().getIndexTemplate(r -> r.name(namePattern)).indexTemplates().size();
  }

  @Override
  public long searchByKey(final String indexPattern, final long key) throws Exception {
    final var resp =
        adminClient.search(
            s ->
                s.index(indexPattern)
                    .query(
                        q -> q.term(t -> t.field("key").value(FieldValue.of(String.valueOf(key))))),
            Object.class);
    return resp.hits().total() == null ? 0 : resp.hits().total().value();
  }

  private OpensearchExporterConfiguration exporterAdminConfig() {
    final var cfg = new OpensearchExporterConfiguration();
    cfg.url = url;
    cfg.getAuthentication().setUsername(container.getUsername());
    cfg.getAuthentication().setPassword(container.getPassword());
    cfg.index.createTemplate = true;
    return cfg;
  }
}
