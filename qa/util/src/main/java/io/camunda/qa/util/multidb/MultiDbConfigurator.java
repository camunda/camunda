/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static io.camunda.configuration.beans.LegacySearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TEST_INTEGRATION_RDBMS_FAST_INIT;

import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;

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
              cfg.getData().getSecondaryStorage().getRetention().setEnabled(retentionEnabled);
              // 0s causes ILM to move data asap - it is normally the default
              // https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-index-lifecycle.html#ilm-phase-transitions
              cfg.getData().getSecondaryStorage().getRetention().setMinimumAge("0s");
              cfg.getData().getSecondaryStorage().getElasticsearch().setUrl(elasticsearchUrl);
              cfg.getData().getSecondaryStorage().getElasticsearch().setIndexPrefix(indexPrefix);
              cfg.getData()
                  .getSecondaryStorage()
                  .getElasticsearch()
                  .getHistory()
                  .setPolicyName(indexPrefix + "-ilm");
              final var history =
                  cfg.getData().getSecondaryStorage().getElasticsearch().getHistory();
              // find completed instances almost directly
              history.setWaitPeriodBeforeArchiving(retentionEnabled ? "1s" : "1h");
              history.setDelayBetweenRuns(Duration.ofMillis(1000));
              history.setMaxDelayBetweenRuns(Duration.ofMillis(1000));
              cfg.getData().getSecondaryStorage().getElasticsearch().getBulk().setSize(1);
              overrideRefreshInterval(cfg.getData().getSecondaryStorage().getElasticsearch());
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
    opensearchProperties.put("camunda.tasklist.opensearch.aws-enabled", isAws);

    /* Operate */
    opensearchProperties.put("camunda.operate.opensearch.aws-enabled", isAws);

    /* Camunda */
    opensearchProperties.put(CREATE_SCHEMA_PROPERTY, true);
    opensearchProperties.put("camunda.database.aws-enabled", isAws);

    testApplication.withAdditionalProperties(opensearchProperties);

    /* Unified Config */
    testApplication
        .withSecondaryStorageType(SecondaryStorageType.opensearch)
        .withUnifiedConfig(
            cfg -> {
              cfg.getData().getSecondaryStorage().getRetention().setEnabled(retentionEnabled);
              cfg.getData().getSecondaryStorage().getRetention().setMinimumAge("0s");
              cfg.getData().getSecondaryStorage().getOpensearch().setUrl(opensearchUrl);
              cfg.getData().getSecondaryStorage().getOpensearch().setIndexPrefix(indexPrefix);
              cfg.getData()
                  .getSecondaryStorage()
                  .getOpensearch()
                  .getHistory()
                  .setPolicyName(indexPrefix + "-ilm");
              cfg.getData().getSecondaryStorage().getOpensearch().setUsername(userName);
              cfg.getData().getSecondaryStorage().getOpensearch().setPassword(userPassword);
              cfg.getData().getSecondaryStorage().getOpensearch().setAwsEnabled(isAws);
              // find completed instances almost directly
              cfg.getData()
                  .getSecondaryStorage()
                  .getOpensearch()
                  .getHistory()
                  .setWaitPeriodBeforeArchiving(retentionEnabled ? "1s" : "1h");
              cfg.getData().getSecondaryStorage().getOpensearch().getBulk().setSize(1);
              overrideRefreshInterval(cfg.getData().getSecondaryStorage().getOpensearch());
            });
  }

  public void configureRDBMSSupport(
      final boolean retentionEnabled,
      final String url,
      final String username,
      final String password) {
    final String tablePrefix = generateTablePrefix();

    if ("true".equalsIgnoreCase(System.getProperty(TEST_INTEGRATION_RDBMS_FAST_INIT))) {
      testApplication.withProperty("camunda.data.secondary-storage.rdbms.auto-ddl", "false");
      if (testApplication instanceof final TestSpringApplication<?> springApp) {
        springApp.withAdditionalInitializer(
            ctx -> {
              final var bd = new GenericBeanDefinition();
              bd.setBeanClass(ScriptBasedSchemaManager.class);
              bd.setPrimary(true);
              ((BeanDefinitionRegistry) ctx.getBeanFactory())
                  .registerBeanDefinition("rdbmsSchemaManager", bd);
            });
      }
    }

    testApplication
        .withSecondaryStorageType(SecondaryStorageType.rdbms)
        .withUnifiedConfig(
            cfg -> {
              final var rdbms = cfg.getData().getSecondaryStorage().getRdbms();
              rdbms.setUrl(url);
              rdbms.setUsername(username);
              rdbms.setPassword(password);
              rdbms.setPrefix(tablePrefix);
              rdbms.setFlushInterval(Duration.ZERO);
              if (retentionEnabled) {
                rdbms.getHistory().setDefaultHistoryTTL(Duration.ofSeconds(1));
                rdbms.getHistory().setMinHistoryCleanupInterval(Duration.ofSeconds(1));
                rdbms.getHistory().setMaxHistoryCleanupInterval(Duration.ofSeconds(5));
                rdbms.getHistory().setDefaultBatchOperationHistoryTTL(Duration.ofSeconds(1));
                rdbms.getHistory().setDecisionInstanceTTL(Duration.ofSeconds(1));
              } else {
                rdbms.getHistory().setDefaultHistoryTTL(Duration.ofHours(1));
                rdbms.getHistory().setMinHistoryCleanupInterval(Duration.ofHours(1));
                rdbms.getHistory().setMaxHistoryCleanupInterval(Duration.ofHours(2));
                rdbms.getHistory().setDefaultBatchOperationHistoryTTL(Duration.ofHours(1));
                rdbms.getHistory().setDecisionInstanceTTL(Duration.ofHours(1));
              }
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
    db.setRefreshInterval("100ms");
  }
}
