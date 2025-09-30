/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

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
  protected String getZeebeExporterIndexPrefixConfigParameterName() {
    return "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_INDEXPREFIX";
  }

  @Override
  protected Map<String, String> getDatabaseEnvironmentVariables(final String indexPrefix) {
    final String dbUrl = "http://host.testcontainers.internal:9200";
    final String dbType = "elasticsearch";
    final String exporterClassName = "io.camunda.exporter.CamundaExporter";

    return Map.ofEntries(
        // Unified Configuration: DB URL + compatibility
        Map.entry("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_URL", dbUrl),
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL", dbUrl),
        Map.entry("CAMUNDA_DATABASE_URL", dbUrl),
        Map.entry("CAMUNDA_TASKLIST_ELASTICSEARCH_URL", dbUrl),
        Map.entry("CAMUNDA_OPERATE_ELASTICSEARCH_URL", dbUrl),
        // Unified Configuration: DB type + compatibility
        Map.entry("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", dbType),
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE", dbType),
        Map.entry("CAMUNDA_OPERATE_DATABASE", dbType),
        Map.entry("CAMUNDA_TASKLIST_DATABASE", dbType),
        Map.entry("CAMUNDA_DATABASE_TYPE", dbType),
        Map.entry("CAMUNDA_DATA_SECONDARYSTORAGE_ELASTICSEARCH_INDEXPREFIX", indexPrefix),
        // ---
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE", "1"),
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_INDEXPREFIX", indexPrefix),
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME", exporterClassName),
        Map.entry("CAMUNDA_DATABASE_INDEXPREFIX", indexPrefix));
  }

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
