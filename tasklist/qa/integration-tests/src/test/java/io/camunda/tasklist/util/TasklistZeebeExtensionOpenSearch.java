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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = "camunda.data.secondary-storage.type", havingValue = "opensearch")
public class TasklistZeebeExtensionOpenSearch extends TasklistZeebeExtension {

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    super.afterEach(extensionContext);
    if (!failed) {
      TestUtil.removeAllIndices(osClient, getPrefix());
    }
  }

  @Override
  protected String getZeebeExporterIndexPrefixConfigParameterName() {
    return "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_INDEXPREFIX";
  }

  @Override
  protected Map<String, String> getDatabaseEnvironmentVariables(final String indexPrefix) {
    final String dbUrl = "http://host.testcontainers.internal:9200";
    final String dbType = "opensearch";

    return Map.ofEntries(
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE", "1"),
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_INDEXPREFIX", indexPrefix),
        Map.entry(
            "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME",
            "io.camunda.exporter.CamundaExporter"),
        // Unified Config: db type + compatibility vars
        Map.entry("CAMUNDA_DATABASE_TYPE", dbType),
        Map.entry("CAMUNDA_DATA_SECONDARYSTORAGE_TYPE", dbType),
        Map.entry("CAMUNDA_OPERATE_DATABASE", dbType),
        Map.entry("CAMUNDA_TASKLIST_DATABASE", dbType),
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE", dbType),
        Map.entry("CAMUNDA_DATA_SECONDARYSTORAGE_OPENSEARCH_INDEXPREFIX", indexPrefix),
        // Unified Config: db url + compatibility vars
        Map.entry("CAMUNDA_DATABASE_URL", dbUrl),
        Map.entry("CAMUNDA_DATA_SECONDARYSTORAGE_OPENSEARCH_URL", dbUrl),
        Map.entry("CAMUNDA_OPERATE_OPENSEARCH_URL", dbUrl),
        Map.entry("CAMUNDA_TASKLIST_OPENSEARCH_URL", dbUrl),
        Map.entry("ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL", dbUrl),
        // ---
        Map.entry("CAMUNDA_DATABASE_INDEXPREFIX", indexPrefix));
  }

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
