/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;

import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class to configure any {@link TestStandaloneApplication}, with specific secondary storage.
 */
public class MultiDbConfigurator {
  public static String zeebePrefix = "zeebe-records";

  private final TestStandaloneApplication<?> testApplication;
  private String indexPrefix;

  public MultiDbConfigurator(final TestStandaloneApplication<?> testApplication) {
    this.testApplication = testApplication;
  }

  public void configureElasticsearchSupportIncludingOldExporter(
      final String elasticsearchUrl, final String indexPrefix) {
    configureElasticsearchSupport(elasticsearchUrl, indexPrefix);

    testApplication.withExporter(
        ElasticsearchExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(ElasticsearchExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "url", elasticsearchUrl,
                  "index", Map.of("prefix", zeebeIndexPrefix()),
                  "bulk", Map.of("size", 1)));
        });
  }

  public void configureElasticsearchSupport(
      final String elasticsearchUrl, final String indexPrefix) {
    configureElasticsearchSupport(elasticsearchUrl, indexPrefix, false);
  }

  public void configureElasticsearchSupport(
      final String elasticsearchUrl, final String indexPrefix, final boolean retentionEnabled) {
    this.indexPrefix = indexPrefix;
    final Map<String, Object> elasticsearchProperties = new HashMap<>();

    /* Tasklist */
    elasticsearchProperties.put("camunda.tasklist.zeebeElasticsearch.prefix", zeebeIndexPrefix());

    /* Camunda */
    elasticsearchProperties.put(CREATE_SCHEMA_PROPERTY, true);

    testApplication.withAdditionalProperties(elasticsearchProperties);
    testApplication
        .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
        .withUnifiedConfig(
            cfg -> {
              final var secondaryStorage = cfg.getData().getSecondaryStorage();
              final var elasticsearch = secondaryStorage.getElasticsearch();

              // Connection settings
              elasticsearch.setUrl(elasticsearchUrl);
              elasticsearch.setIndexPrefix(indexPrefix);

              // Retention settings
              secondaryStorage.getRetention().setEnabled(retentionEnabled);
              // 0s causes ILM to move data asap - it is normally the default
              // https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-index-lifecycle.html#ilm-phase-transitions
              secondaryStorage.getRetention().setMinimumAge("0s");

              // History settings
              final var history = elasticsearch.getHistory();
              history.setPolicyName(indexPrefix + "-ilm");
              history.setWaitPeriodBeforeArchiving(retentionEnabled ? "1s" : "1h");
              history.setDelayBetweenRuns(java.time.Duration.ofMillis(1000));
              history.setMaxDelayBetweenRuns(java.time.Duration.ofMillis(1000));

              // Bulk settings
              elasticsearch.getBulk().setSize(1);

              overrideRefreshInterval(elasticsearch);
            });
  }

  public void configureOpenSearchSupportIncludingOldExporter(
      final String opensearchUrl,
      final String indexPrefix,
      final String userName,
      final String userPassword) {
    configureOpenSearchSupport(opensearchUrl, indexPrefix, userName, userPassword);

    testApplication.withExporter(
        OpensearchExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(OpensearchExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "url",
                  opensearchUrl,
                  "index",
                  Map.of("prefix", zeebeIndexPrefix()),
                  "bulk",
                  Map.of("size", 1),
                  "authentication",
                  Map.of("username", userName, "password", userPassword)));
        });
  }

  public void configureOpenSearchSupport(
      final String opensearchUrl,
      final String indexPrefix,
      final String userName,
      final String userPassword) {
    configureOpenSearchSupport(opensearchUrl, indexPrefix, userName, userPassword, false, false);
  }

  public void configureOpenSearchSupport(
      final String opensearchUrl,
      final String indexPrefix,
      final String userName,
      final String userPassword,
      final boolean retentionEnabled,
      final boolean isAws) {
    this.indexPrefix = indexPrefix;

    final Map<String, Object> opensearchProperties = new HashMap<>();

    /* Tasklist */
    opensearchProperties.put("camunda.tasklist.zeebeOpensearch.prefix", zeebeIndexPrefix());
    opensearchProperties.put("camunda.tasklist.opensearch.aws.enabled", isAws);

    /* Operate */
    opensearchProperties.put("camunda.operate.opensearch.aws.enabled", isAws);

    /* Camunda */
    opensearchProperties.put(CREATE_SCHEMA_PROPERTY, true);
    opensearchProperties.put("camunda.database.aws-enabled", isAws);

    testApplication.withAdditionalProperties(opensearchProperties);

    /* Unified Config */
    testApplication
        .withSecondaryStorageType(SecondaryStorageType.opensearch)
        .withUnifiedConfig(
            cfg -> {
              final var secondaryStorage = cfg.getData().getSecondaryStorage();
              final var opensearch = secondaryStorage.getOpensearch();

              // Connection settings
              opensearch.setUrl(opensearchUrl);
              opensearch.setIndexPrefix(indexPrefix);
              opensearch.setUsername(userName);
              opensearch.setPassword(userPassword);

              // Retention settings
              secondaryStorage.getRetention().setEnabled(retentionEnabled);
              // 0s causes ILM to move data asap - it is normally the default
              // https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-index-lifecycle.html#ilm-phase-transitions
              secondaryStorage.getRetention().setMinimumAge("0s");

              // History settings
              final var history = opensearch.getHistory();
              history.setPolicyName(indexPrefix + "-ilm");
              history.setWaitPeriodBeforeArchiving(retentionEnabled ? "1s" : "1h");

              // Bulk settings
              opensearch.getBulk().setSize(1);

              overrideRefreshInterval(opensearch);
            });
  }

  public void configureRDBMSSupport(
      final boolean retentionEnabled,
      final String url,
      final String username,
      final String password,
      final String driverClass) {
    // db type
    testApplication.withSecondaryStorageType(SecondaryStorageType.rdbms);

    testApplication.withProperty(
        "camunda.data.secondary-storage.rdbms.prefix", generateTablePrefix());
    // --

    testApplication.withProperty("spring.datasource.url", url);
    testApplication.withProperty("spring.datasource.driver-class-name", driverClass);
    testApplication.withProperty("spring.datasource.username", username);
    testApplication.withProperty("spring.datasource.password", password);
    testApplication.withProperty("logging.level.io.camunda.db.rdbms", "DEBUG");
    testApplication.withProperty("logging.level.org.mybatis", "DEBUG");

    // Since the property override from unified configuration is not applied in this test setup, we
    // have to build the RDBMS exporter manually
    testApplication.withExporter(
        "rdbms",
        cfg -> {
          cfg.setClassName("io.camunda.db.rdbms.exporter.RdbmsExporter");
          cfg.setArgs(
              Map.of(
                  "flushInterval",
                  "PT0S",
                  "history",
                  Map.of(
                      "defaultHistoryTTL",
                      retentionEnabled ? "PT1S" : "PT1H",
                      "minHistoryCleanupInterval",
                      retentionEnabled ? "PT1S" : "PT1H",
                      "maxHistoryCleanupInterval",
                      retentionEnabled ? "PT5S" : "PT2H",
                      "defaultBatchOperationHistoryTTL",
                      retentionEnabled ? "PT1S" : "PT1H")));
        });
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public String zeebeIndexPrefix() {
    return indexPrefix != null && !indexPrefix.isBlank()
        ? indexPrefix + "-" + zeebePrefix
        : zeebePrefix;
  }

  public void configureAWSOpenSearchSupport(
      final String opensearchUrl, final String indexPrefix, final boolean isHistoryRelatedTest) {
    configureOpenSearchSupport(opensearchUrl, indexPrefix, "", "", isHistoryRelatedTest, true);
    testApplication.withExporter(
        OpensearchExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(OpensearchExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "url",
                  opensearchUrl,
                  "index",
                  Map.of("prefix", indexPrefix),
                  "bulk",
                  Map.of("size", 1),
                  "aws",
                  Map.of("enabled", true)));
        });
  }

  /**
   * Generates a random table prefix based on the given prefix by creating a random string.
   *
   * @return the table prefix
   */
  private static String generateTablePrefix() {
    final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    final StringBuilder sb = new StringBuilder(10);
    final java.util.Random random = new java.util.Random();
    for (int i = 0; i < 10; i++) {
      sb.append(chars.charAt(random.nextInt(chars.length())));
    }
    return sb.toString();
  }

  private static void overrideRefreshInterval(final DocumentBasedSecondaryStorageDatabase db) {
    // make refresh interval lower so tests can run a bit faster
    db.setRefreshIntervalByIndexName(Map.of("list-view", "1s"));
  }
}
