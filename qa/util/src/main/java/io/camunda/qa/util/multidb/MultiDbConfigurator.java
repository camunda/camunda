/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TEST_INTEGRATION_RDBMS_FAST_INIT;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import java.time.Duration;
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
    testApplication
        .withSecondaryStorageType(SecondaryStorageType.elasticsearch)
        .withUnifiedConfig(
            cfg -> {
              configureDocumentBasedStorage(
                  cfg, elasticsearchUrl, indexPrefix, "", "", retentionEnabled);
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
    /* Unified Config */
    testApplication
        .withSecondaryStorageType(SecondaryStorageType.opensearch)
        .withUnifiedConfig(
            cfg -> {
              configureDocumentBasedStorage(
                  cfg, opensearchUrl, indexPrefix, userName, userPassword, retentionEnabled);
              cfg.getData().getSecondaryStorage().getOpensearch().setAwsEnabled(isAws);
            });
  }

  private void configureDocumentBasedStorage(
      final Camunda cfg,
      final String url,
      final String indexPrefix,
      final String userName,
      final String userPassword,
      final boolean retentionEnabled) {
    cfg.getData().getSecondaryStorage().getRetention().setEnabled(retentionEnabled);
    cfg.getData().getSecondaryStorage().getRetention().setMinimumAge("0s");
    final var documentBasedDatabase =
        cfg.getData().getSecondaryStorage().getDocumentBasedDatabase();
    documentBasedDatabase.setCreateSchema(true);
    documentBasedDatabase.setUrl(url);
    documentBasedDatabase.setIndexPrefix(indexPrefix);
    documentBasedDatabase.getHistory().setPolicyName(indexPrefix + "-ilm");
    if (userName != null && !userName.isBlank()) {
      documentBasedDatabase.setUsername(userName);
    }
    if (userPassword != null && !userPassword.isBlank()) {
      documentBasedDatabase.setPassword(userPassword);
    }
    documentBasedDatabase.getHistory().setWaitPeriodBeforeArchiving(retentionEnabled ? "1s" : "1h");
    documentBasedDatabase.getHistory().setDelayBetweenRuns(Duration.ofMillis(1000));
    documentBasedDatabase.getHistory().setMaxDelayBetweenRuns(Duration.ofMillis(1000));
    documentBasedDatabase.getBulk().setSize(1);
    overrideRefreshInterval(documentBasedDatabase);
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
