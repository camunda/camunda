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

import io.camunda.exporter.CamundaExporter;
import io.camunda.search.connect.configuration.DatabaseType;
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
    elasticsearchProperties.put("camunda.tasklist.elasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.tasklist.zeebeElasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.tasklist.elasticsearch.indexPrefix", indexPrefix);
    elasticsearchProperties.put("camunda.tasklist.zeebeElasticsearch.prefix", zeebeIndexPrefix());

    /* Operate */
    elasticsearchProperties.put("camunda.operate.elasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.operate.zeebeElasticsearch.url", elasticsearchUrl);
    elasticsearchProperties.put("camunda.operate.elasticsearch.indexPrefix", indexPrefix);
    elasticsearchProperties.put("camunda.operate.zeebeElasticsearch.prefix", zeebeIndexPrefix());

    /* Camunda */
    elasticsearchProperties.put(
        PROPERTY_CAMUNDA_DATABASE_TYPE,
        io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH);
    elasticsearchProperties.put("camunda.database.indexPrefix", indexPrefix);
    elasticsearchProperties.put("camunda.database.url", elasticsearchUrl);
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
    configureOpenSearchSupport(opensearchUrl, indexPrefix, userName, userPassword, false);
  }

  public void configureOpenSearchSupport(
      final String opensearchUrl,
      final String indexPrefix,
      final String userName,
      final String userPassword,
      final boolean retentionEnabled) {
    this.indexPrefix = indexPrefix;

    final Map<String, Object> opensearchProperties = new HashMap<>();

    /* Tasklist */
    opensearchProperties.put("camunda.tasklist.opensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.tasklist.zeebeOpensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.tasklist.opensearch.indexPrefix", indexPrefix);
    opensearchProperties.put("camunda.tasklist.zeebeOpensearch.prefix", zeebeIndexPrefix());
    opensearchProperties.put("camunda.tasklist.opensearch.username", userName);
    opensearchProperties.put("camunda.tasklist.opensearch.password", userPassword);

    /* Operate */
    opensearchProperties.put("camunda.operate.opensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.operate.zeebeOpensearch.url", opensearchUrl);
    opensearchProperties.put("camunda.operate.opensearch.indexPrefix", indexPrefix);
    opensearchProperties.put("camunda.operate.zeebeOpensearch.prefix", zeebeIndexPrefix());
    opensearchProperties.put("camunda.operate.opensearch.username", userName);
    opensearchProperties.put("camunda.operate.opensearch.password", userPassword);

    /* Camunda */
    opensearchProperties.put(
        PROPERTY_CAMUNDA_DATABASE_TYPE,
        io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH);
    opensearchProperties.put("camunda.operate.database", "opensearch");
    opensearchProperties.put("camunda.tasklist.database", "opensearch");
    opensearchProperties.put("camunda.database.indexPrefix", indexPrefix);
    opensearchProperties.put("camunda.database.username", userName);
    opensearchProperties.put("camunda.database.password", userPassword);
    opensearchProperties.put("camunda.database.url", opensearchUrl);
    opensearchProperties.put(
        "camunda.database.retention.enabled", Boolean.toString(retentionEnabled));
    opensearchProperties.put("camunda.database.retention.policyName", indexPrefix + "-ilm");
    opensearchProperties.put("camunda.database.retention.minimumAge", "0s");
    opensearchProperties.put(CREATE_SCHEMA_PROPERTY, true);

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
                      userPassword),
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
                          "0s")),
                  "bulk",
                  Map.of("size", 1)));
        });
  }

  public void configureRDBMSSupport(final boolean retentionEnabled) {
    testApplication.withProperty(PROPERTY_CAMUNDA_DATABASE_TYPE, DatabaseType.RDBMS);
    testApplication.withProperty(
        "spring.datasource.url",
        "jdbc:h2:mem:testdb+" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    testApplication.withProperty("spring.datasource.driver-class-name", "org.h2.Driver");
    testApplication.withProperty("spring.datasource.username", "sa");
    testApplication.withProperty("spring.datasource.password", "");
    testApplication.withProperty("zeebe.broker.exporters.rdbms.args.flushInterval", "PT0S");
    testApplication.withProperty(
        "zeebe.broker.exporters.rdbms.args.history.defaultHistoryTTL",
        retentionEnabled ? "PT1S" : "PT1H");
    testApplication.withProperty(
        "zeebe.broker.exporters.rdbms.args.history.defaultBatchOperationHistoryTTL",
        retentionEnabled ? "PT1S" : "PT1H");
    testApplication.withProperty(
        "zeebe.broker.exporters.rdbms.args.history.minHistoryCleanupInterval",
        retentionEnabled ? "PT1S" : "PT1H");
    testApplication.withProperty(
        "zeebe.broker.exporters.rdbms.args.history.maxHistoryCleanupInterval",
        retentionEnabled ? "PT5S" : "PT2H");
    testApplication.withExporter(
        "rdbms",
        cfg -> {
          cfg.setClassName("-");
        });
    testApplication.withProperty("logging.level.io.camunda.db.rdbms", "DEBUG");
    testApplication.withProperty("logging.level.org.mybatis", "DEBUG");
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public String zeebeIndexPrefix() {
    return indexPrefix != null && !indexPrefix.isBlank()
        ? indexPrefix + "-" + zeebePrefix
        : zeebePrefix;
  }

  public void configureAWSOpenSearchSupport(final String opensearchUrl, final String indexPrefix) {
    configureOpenSearchSupport(opensearchUrl, indexPrefix, "", "");
    final Map<String, Object> opensearchProperties = new HashMap<>();
    opensearchProperties.put("camunda.tasklist.opensearch.aws.enabled", true);
    opensearchProperties.put("camunda.operate.opensearch.aws.enabled", true);
    testApplication.withAdditionalProperties(opensearchProperties);
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
