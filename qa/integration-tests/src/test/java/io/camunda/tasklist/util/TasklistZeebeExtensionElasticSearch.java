/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "camunda.tasklist.database",
    havingValue = "elasticsearch",
    matchIfMissing = true)
public class TasklistZeebeExtensionElasticSearch extends TasklistZeebeExtension {

  @Autowired
  @Qualifier("zeebeEsClient")
  protected RestHighLevelClient zeebeEsClient;

  public void refreshIndices(Instant instant) {
    try {
      final String date =
          DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(instant);
      final RefreshRequest refreshRequest = new RefreshRequest(getPrefix() + "*" + date);
      zeebeEsClient.indices().refresh(refreshRequest, RequestOptions.DEFAULT);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void setZeebeIndexesPrefix(String prefix) {
    tasklistProperties.getZeebeElasticsearch().setPrefix(prefix);
  }

  @Override
  protected void setDatabaseEnvironmentVariables(ZeebeContainer zeebeContainer) {
    zeebeContainer
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL",
            "http://host.testcontainers.internal:9200")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_DELAY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX", getPrefix())
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.ElasticsearchExporter");
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) {
    super.afterEach(extensionContext);
    if (!failed) {
      TestUtil.removeAllIndices(zeebeEsClient, getPrefix());
    }
  }

  @Override
  public void setZeebeOsClient(OpenSearchClient zeebeOsClient) {}

  @Override
  public void setZeebeEsClient(RestHighLevelClient zeebeOsClient) {
    this.zeebeEsClient = zeebeOsClient;
  }

  @Override
  protected int getDatabasePort() {
    return 9200;
  }
}
