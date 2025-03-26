/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.tasklist.qa.util.TestUtil;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
@ConditionalOnProperty(name = "camunda.tasklist.database", havingValue = "opensearch")
public class TasklistZeebeExtensionOpenSearch extends TasklistZeebeExtension {

  @Autowired
  @Qualifier("tasklistZeebeOsClient")
  private OpenSearchClient zeebeOsClient;

  @Override
  public void refreshIndices(final Instant instant) {
    try {
      final String date =
          DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(instant);
      zeebeOsClient.indices().refresh(r -> r.index(getPrefix() + "*" + date));
    } catch (final IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    super.afterEach(extensionContext);
    if (!failed) {
      TestUtil.removeAllIndices(zeebeOsClient, getPrefix());
    }
  }

  @Override
  protected void setZeebeIndexesPrefix(final String prefix) {
    tasklistProperties.getZeebeOpenSearch().setPrefix(prefix);
  }

  @Override
  protected String getZeebeExporterIndexPrefixConfigParameterName() {
    return "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_PREFIX";
  }

  @Override
  protected Map<String, String> getDatabaseEnvironmentVariables(final String indexPrefix) {
    return Map.of(
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL",
        "http://host.testcontainers.internal:9200",
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE",
        ConnectionTypes.OPENSEARCH.name(),
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE",
        "1",
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_INDEX_PREFIX",
        indexPrefix,
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME",
        "io.camunda.exporter.CamundaExporter",
        "CAMUNDA_DATABASE_TYPE",
        "opensearch",
        "CAMUNDA_DATABASE_URL",
        "http://host.testcontainers.internal:9200",
        "CAMUNDA_DATABASE_INDEX_PREFIX",
        indexPrefix);
  }

  @Override
  public void setZeebeOsClient(final OpenSearchClient zeebeOsClient) {
    this.zeebeOsClient = zeebeOsClient;
  }

  @Override
  public void setZeebeEsClient(final RestHighLevelClient zeebeEsClient) {}

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
