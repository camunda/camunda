/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.tasklist.qa.util.TestUtil;
import java.util.Map;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@ConditionalOnProperty(
    name = "camunda.data.secondary-storage.type",
    havingValue = "elasticsearch",
    matchIfMissing = true)
public class TasklistZeebeExtensionElasticSearch extends TasklistZeebeExtension {

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    super.afterEach(extensionContext);
    if (!failed) {
      TestUtil.removeAllIndices(esClient, getPrefix());
    }
  }

  @Override
  protected DatabaseType getDatabaseType() {
    return DatabaseType.ELASTICSEARCH;
  }

  @Override
  protected Map<String, String> getDatabaseEnvironmentVariables(final String indexPrefix) {
    final String dbUrl = "http://host.testcontainers.internal:9200";

    return Map.ofEntries(
        // Unified Configuration: DB URL + compatibility
        Map.entry("camunda.data.secondary-storage.elasticsearch.url", dbUrl),
        Map.entry("camunda.database.url", dbUrl),
        Map.entry("camunda.tasklist.elasticsearch.url", dbUrl),
        Map.entry("camunda.operate.elasticsearch.url", dbUrl),
        // Unified Configuration: DB type + compatibility
        Map.entry("camunda.data.secondary-storage.type", getDatabaseType().toString()),
        Map.entry("camunda.operate.database", getDatabaseType().toString()),
        Map.entry("camunda.tasklist.database", getDatabaseType().toString()),
        Map.entry("camunda.database.type", getDatabaseType().toString()),
        Map.entry("camunda.data.secondary-storage.elasticsearch.index-prefix", indexPrefix),
        // ---
        Map.entry("camunda.database.index-prefix", indexPrefix));
  }

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
