/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import io.camunda.exporter.CamundaExporter;
import io.camunda.operate.property.OperateOpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.tasklist.property.TasklistOpenSearchProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.zeebe.exporter.ElasticsearchExporter;
import io.camunda.zeebe.exporter.opensearch.OpensearchExporter;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class to configure any {@link TestStandaloneApplication}, with specific secondary storage.
 */
public class MultiDbConfigurator {

  private final TestStandaloneApplication<?> testApplication;

  private final OperateProperties operateProperties;
  private final TasklistProperties tasklistProperties;

  public MultiDbConfigurator(final TestStandaloneApplication<?> testApplication) {
    this.testApplication = testApplication;
    // we are configuring this always, even if we might not use the applications
    operateProperties = new OperateProperties();
    tasklistProperties = new TasklistProperties();

    testApplication
        .withBean("operate-config", operateProperties, OperateProperties.class)
        .withBean("tasklist-config", tasklistProperties, TasklistProperties.class);
  }

  public void configureElasticsearchSupport(
      final String elasticsearchUrl, final String indexPrefix) {
    operateProperties.getElasticsearch().setUrl(elasticsearchUrl);
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
    operateProperties.getZeebeElasticsearch().setUrl(elasticsearchUrl);
    operateProperties.getZeebeElasticsearch().setPrefix(indexPrefix);
    tasklistProperties.getElasticsearch().setUrl(elasticsearchUrl);
    tasklistProperties.getElasticsearch().setIndexPrefix(indexPrefix);
    tasklistProperties.getZeebeElasticsearch().setUrl(elasticsearchUrl);
    tasklistProperties.getZeebeElasticsearch().setPrefix(indexPrefix);
    testApplication.withExporter(
        "CamundaExporter",
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
                  "bulk",
                  Map.of("size", 1)));
        });

    testApplication.withExporter(
        "ElasticsearchExporter",
        cfg -> {
          cfg.setClassName(ElasticsearchExporter.class.getName());
          cfg.setArgs(
              Map.of(
                  "url", elasticsearchUrl,
                  "index", Map.of("prefix", indexPrefix),
                  "bulk", Map.of("size", 1)));
        });

    testApplication.withProperty(
        "camunda.database.type",
        io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH);
    testApplication.withProperty("camunda.database.indexPrefix", indexPrefix);
    testApplication.withProperty("camunda.database.url", elasticsearchUrl);
  }

  public void configureOpenSearchSupport(
      final String opensearchUrl,
      final String indexPrefix,
      final String userName,
      final String userPassword) {
    final OperateOpensearchProperties operateOpensearch = operateProperties.getOpensearch();
    operateOpensearch.setUrl(opensearchUrl);
    operateOpensearch.setIndexPrefix(indexPrefix);
    operateOpensearch.setPassword(userPassword);
    operateOpensearch.setUsername(userName);

    final var zeebeOS = operateProperties.getZeebeOpensearch();
    zeebeOS.setUrl(opensearchUrl);
    zeebeOS.setPassword(userPassword);
    zeebeOS.setUsername(userName);
    zeebeOS.setPrefix(indexPrefix);

    tasklistProperties.setDatabase("opensearch");
    final TasklistOpenSearchProperties tasklistOpensearch = tasklistProperties.getOpenSearch();
    tasklistOpensearch.setUrl(opensearchUrl);
    tasklistOpensearch.setIndexPrefix(indexPrefix);
    tasklistOpensearch.setPassword(userPassword);
    tasklistOpensearch.setUsername(userName);

    final var zeebeTasklistOS = tasklistProperties.getZeebeOpenSearch();
    zeebeTasklistOS.setUrl(opensearchUrl);
    zeebeTasklistOS.setPassword(userPassword);
    zeebeTasklistOS.setUsername(userName);
    zeebeTasklistOS.setPrefix(indexPrefix);

    testApplication.withExporter(
        "CamundaExporter",
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
                  "bulk",
                  Map.of("size", 1)));
        });

    testApplication.withExporter(
        "OpensearchExporter",
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
                  "authentication",
                  Map.of("username", userName, "password", userPassword)));
        });

    testApplication.withProperty(
        "camunda.database.type", io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH);
    testApplication.withProperty("camunda.operate.database", "opensearch");
    testApplication.withProperty("camunda.tasklist.database", "opensearch");
    testApplication.withProperty("camunda.database.indexPrefix", indexPrefix);
    testApplication.withProperty("camunda.database.username", userName);
    testApplication.withProperty("camunda.database.password", userPassword);
    testApplication.withProperty("camunda.database.url", opensearchUrl);
  }

  public void configureRDBMSSupport() {
    testApplication.withProperty("camunda.database.type", DatabaseType.RDBMS);
    testApplication.withProperty(
        "spring.datasource.url",
        "jdbc:h2:mem:testdb+" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    testApplication.withProperty("spring.datasource.driver-class-name", "org.h2.Driver");
    testApplication.withProperty("spring.datasource.username", "sa");
    testApplication.withProperty("spring.datasource.password", "");
    testApplication.withProperty("zeebe.broker.exporters.rdbms.args.flushInterval", "0");
    testApplication.withProperty("zeebe.broker.exporters.rdbms.args.defaultHistoryTTL", "PT2S");
    testApplication.withProperty("zeebe.broker.exporters.rdbms.args.minHistoryCleanupInterval", "PT2S");
    testApplication.withProperty("zeebe.broker.exporters.rdbms.args.maxHistoryCleanupInterval", "PT5S");
    testApplication.withExporter(
        "rdbms",
        cfg -> {cfg.setClassName("-");});
    testApplication.withProperty("logging.level.io.camunda.db.rdbms", "DEBUG");
    testApplication.withProperty("logging.level.org.mybatis", "DEBUG");
  }

  public OperateProperties getOperateProperties() {
    return operateProperties;
  }

  public TasklistProperties getTasklistProperties() {
    return tasklistProperties;
  }
}
