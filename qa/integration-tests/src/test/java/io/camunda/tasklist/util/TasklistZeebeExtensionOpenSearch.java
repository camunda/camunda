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
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "camunda.tasklist.database", havingValue = "opensearch")
public class TasklistZeebeExtensionOpenSearch extends TasklistZeebeExtension {

  @Autowired
  @Qualifier("zeebeOsClient")
  protected OpenSearchClient zeebeOsClient;

  public void refreshIndices(Instant instant) {
    try {
      final String date =
          DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(instant);
      zeebeOsClient.indices().refresh(r -> r.index(getPrefix() + "*" + date));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void setZeebeIndexesPrefix(String prefix) {
    tasklistProperties.getZeebeOpenSearch().setPrefix(prefix);
  }

  @Override
  protected void setDatabaseEnvironmentVariables(ZeebeContainer zeebeContainer) {
    zeebeContainer
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_URL",
            "http://host.testcontainers.internal:9205")
        .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULK_DELAY", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_BULK_SIZE", "1")
        .withEnv("ZEEBE_BROKER_EXPORTERS_OPENSEARCH_ARGS_INDEX_PREFIX", getPrefix())
        .withEnv(
            "ZEEBE_BROKER_EXPORTERS_OPENSEARCH_CLASSNAME",
            "io.camunda.zeebe.exporter.opensearch.OpensearchExporter");
  }

  @Override
  public void afterEach(ExtensionContext extensionContext) {
    super.afterEach(extensionContext);
    if (!failed) {
      TestUtil.removeAllIndices(zeebeOsClient, getPrefix());
    }
  }

  @Override
  public void setZeebeEsClient(RestHighLevelClient zeebeEsClient) {}

  @Override
  protected int getDatabasePort() {
    return 9205;
  }

  public void setZeebeOsClient(final OpenSearchClient zeebeOsClient) {
    this.zeebeOsClient = zeebeOsClient;
  }
}
