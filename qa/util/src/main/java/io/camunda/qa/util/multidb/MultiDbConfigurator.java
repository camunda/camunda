/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;
import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;
import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.camunda.exporter.CamundaExporter;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class to configure any {@link TestStandaloneApplication}, with specific secondary storage.
 */
public class MultiDbConfigurator {
  public static String zeebePrefix = "zeebe-records";

  private static final String DB_TYPE_ELASTICSEARCH = "elasticsearch";
  private static final String DB_TYPE_OPENSEARCH = "opensearch";
  private static final String DB_TYPE_RDBMS = "rdbms";

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

    /* Operate */
    elasticsearchProperties.put("camunda.operate.zeebeElasticsearch.prefix", zeebeIndexPrefix());

    // indexPrefix
    elasticsearchProperties.put(
        "camunda.data.secondary-storage.elasticsearch.index-prefix", indexPrefix);
    // db type
    elasticsearchProperties.put(PROPERTY_CAMUNDA_DATABASE_TYPE, DB_TYPE_ELASTICSEARCH);
    elasticsearchProperties.put(
        UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, DB_TYPE_ELASTICSEARCH);
    elasticsearchProperties.put("camunda.operate.database", DB_TYPE_ELASTICSEARCH);
    elasticsearchProperties.put("camunda.tasklist.database", DB_TYPE_ELASTICSEARCH);
    // url
    elasticsearchProperties.put(
        "camunda.data.secondary-storage.elasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.database.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.tasklist.elasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.tasklist.zeebeElasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.operate.elasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.operate.zeebeElasticsearch.url", elasticsearchUrl);

    /* Camunda */
    elasticsearchProperties.put(
        "camunda.database.retention.enabled", Boolean.toString(retentionEnabled));
    elasticsearchProperties.put("camunda.database.retention.policyName", indexPrefix + "-ilm");
    // 0s causes ILM to move data asap - it is normally the default
    // https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-index-lifecycle.html#ilm-phase-transitions
    elasticsearchProperties.put("camunda.database.retention.minimumAge", "0s");
    elasticsearchProperties.put(CREATE_SCHEMA_PROPERTY, true);

    testApplication.withAdditionalProperties(elasticsearchProperties);

    testApplication.withExporter(
        CamundaExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of(
                      "url",
                      elasticsearchUrl,
                      "indexPrefix",
                      indexPrefix,
                      "type",
                      io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH),
                  "index",
                  Map.of("prefix", indexPrefix),
                  "history",
                  Map.of(
                      "waitPeriodBeforeArchiving",
                      retentionEnabled ? "1s" : "1h", // find completed instances almost directly
                      "retention",
                      Map.of(
                          "enabled",
                          Boolean.toString(retentionEnabled),
                          "policyName",
                          indexPrefix + "-ilm",
                          "minimumAge",
                          // 0s causes ILM to move data asap - it is normally the default
                          // https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-index-lifecycle.html#ilm-phase-transitions
                          "0s",
                          "usageMetricsPolicyName",
                          indexPrefix + "-usage-metrics-ilm",
                          "usageMetricsMinimumAge",
                          "0s")),
                  "bulk",
                  Map.of("size", 1)));
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
    opensearchProperties.put("camunda.tasklist.opensearch.username", userName);
    opensearchProperties.put("camunda.tasklist.opensearch.password", userPassword);
    opensearchProperties.put("camunda.tasklist.opensearch.aws.enabled", isAws);

    /* Operate */
    opensearchProperties.put("camunda.operate.zeebeOpensearch.prefix", zeebeIndexPrefix());
    opensearchProperties.put("camunda.operate.opensearch.username", userName);
    opensearchProperties.put("camunda.operate.opensearch.password", userPassword);
    opensearchProperties.put("camunda.operate.opensearch.aws.enabled", isAws);

    // index prefix
    opensearchProperties.put("camunda.data.secondary-storage.opensearch.index-prefix", indexPrefix);
    // db url
    opensearchProperties.put("camunda.data.secondary-storage.opensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.tasklist.opensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.tasklist.zeebeOpensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.operate.opensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.operate.zeebeOpensearch.url", opensearchUrl);
    // db type
    opensearchProperties.put(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, DB_TYPE_OPENSEARCH);
    opensearchProperties.put(PROPERTY_CAMUNDA_DATABASE_TYPE, DB_TYPE_OPENSEARCH);
    opensearchProperties.put("camunda.operate.database", DB_TYPE_OPENSEARCH);
    opensearchProperties.put("camunda.tasklist.database", DB_TYPE_OPENSEARCH);

    /* Camunda */
    opensearchProperties.put("camunda.database.username", userName);
    opensearchProperties.put("camunda.database.password", userPassword);
    opensearchProperties.put("camunda.database.url", opensearchUrl);
    opensearchProperties.put(
        "camunda.database.retention.enabled", Boolean.toString(retentionEnabled));
    opensearchProperties.put("camunda.database.retention.policyName", indexPrefix + "-ilm");
    opensearchProperties.put("camunda.database.retention.minimumAge", "0s");
    opensearchProperties.put(CREATE_SCHEMA_PROPERTY, true);
    opensearchProperties.put("camunda.database.aws-enabled", isAws);

    /* Unified Config */
    opensearchProperties.put("camunda.data.secondary-storage.opensearch.username", userName);
    opensearchProperties.put("camunda.data.secondary-storage.opensearch.password", userPassword);

    testApplication.withAdditionalProperties(opensearchProperties);

    testApplication.withExporter(
        CamundaExporter.class.getSimpleName().toLowerCase(),
        cfg -> {
          cfg.setClassName(CamundaExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "connect",
                  Map.of(
                      "url",
                      opensearchUrl,
                      "indexPrefix",
                      indexPrefix,
                      "type",
                      io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH,
                      "username",
                      userName,
                      "password",
                      userPassword,
                      "awsEnabled",
                      isAws),
                  "index",
                  Map.of("prefix", indexPrefix),
                  "history",
                  Map.of(
                      "waitPeriodBeforeArchiving",
                      retentionEnabled ? "1s" : "1h", // find completed instances almost directly
                      "retention",
                      Map.of(
                          "enabled",
                          Boolean.toString(retentionEnabled),
                          "policyName",
                          indexPrefix + "-ilm",
                          "minimumAge",
                          // 0s causes ILM to move data asap - it is normally the default
                          // https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-index-lifecycle.html#ilm-phase-transitions
                          "0s",
                          "usageMetricsPolicyName",
                          indexPrefix + "-usage-metrics-ilm",
                          "usageMetricsMinimumAge",
                          "0s")),
                  "bulk",
                  Map.of("size", 1)));
        });
  }

  public void configureRDBMSSupport(final boolean retentionEnabled) {
    // db type
    testApplication.withProperty(PROPERTY_CAMUNDA_DATABASE_TYPE, DB_TYPE_RDBMS);
    testApplication.withProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, DB_TYPE_RDBMS);
    testApplication.withProperty("camunda.operate.database", DB_TYPE_RDBMS); // compatibility
    testApplication.withProperty("camunda.tasklist.database", DB_TYPE_RDBMS); // compatibility
    // --

    testApplication.withProperty(
        "spring.datasource.url",
        "jdbc:h2:mem:testdb+" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    testApplication.withProperty("spring.datasource.driver-class-name", "org.h2.Driver");
    testApplication.withProperty("spring.datasource.username", "sa");
    testApplication.withProperty("spring.datasource.password", "");
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
}
