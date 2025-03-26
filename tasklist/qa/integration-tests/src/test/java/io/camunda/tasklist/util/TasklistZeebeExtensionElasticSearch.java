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
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
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
@ConditionalOnProperty(
    name = "camunda.tasklist.database",
    havingValue = "elasticsearch",
    matchIfMissing = true)
public class TasklistZeebeExtensionElasticSearch extends TasklistZeebeExtension {

  @Autowired
  @Qualifier("tasklistZeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Override
  public void refreshIndices(final Instant instant) {
    try {
      final String date =
          DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(instant);
      final RefreshRequest refreshRequest = new RefreshRequest(getPrefix() + "*" + date);
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (final IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    super.afterEach(extensionContext);
    if (!failed) {
      TestUtil.removeAllIndices(zeebeEsClient, getPrefix());
    }
  }

  @Override
  protected void setZeebeIndexesPrefix(final String prefix) {
    tasklistProperties.getZeebeElasticsearch().setPrefix(prefix);
  }

  @Override
  protected String getZeebeExporterIndexPrefixConfigParameterName() {
    return "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_INDEXPREFIX";
  }

  @Override
  protected Map<String, String> getDatabaseEnvironmentVariables(final String indexPrefix) {
    return Map.of(
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_URL",
        "http://host.testcontainers.internal:9200",
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_TYPE",
        ConnectionTypes.ELASTICSEARCH.name(),
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_BULK_SIZE",
        "1",
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_ARGS_CONNECT_INDEXPREFIX",
        indexPrefix,
        "ZEEBE_BROKER_EXPORTERS_CAMUNDAEXPORTER_CLASSNAME",
        "io.camunda.exporter.CamundaExporter",
        "CAMUNDA_DATABASE_URL",
        "http://host.testcontainers.internal:9200",
        "CAMUNDA_DATABASE_INDEXPREFIX",
        indexPrefix);
  }

  @Override
  public void setZeebeOsClient(final OpenSearchClient zeebeOsClient) {}

  @Override
  public void setZeebeEsClient(final RestHighLevelClient zeebeOsClient) {
    zeebeEsClient = zeebeOsClient;
  }

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
