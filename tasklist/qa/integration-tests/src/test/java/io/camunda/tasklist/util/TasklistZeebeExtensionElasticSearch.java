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
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
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
  protected void setSecondaryStorageConfig(
      final TestStandaloneBroker broker, final String indexPrefix) {
    final String dbUrl = "http://host.testcontainers.internal:9200";

    broker.withAdditionalProperties(
        Map.of(
            "camunda.data.secondary-storage.type",
            "elasticsearch",
            "camunda.data.secondary-storage.elasticsearch.url",
            dbUrl,
            "camunda.data.secondary-storage.elasticsearch.index-prefix",
            indexPrefix));
  }

  @Override
  protected Map<String, String> getLegacyConfiguration(final String indexPrefix) {
    final String dbUrl = "http://host.testcontainers.internal:9200";

    return Map.of(
        "camunda.database.type",
        getDatabaseType().name(),
        "camunda.database.url",
        dbUrl,
        "camunda.tasklist.database",
        getDatabaseType().name(),
        "camunda.operate.database",
        getDatabaseType().name(),
        "camunda.tasklist.elasticsearch.url",
        dbUrl,
        "camunda.operate.elasticsearch.url",
        dbUrl);
  }

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
