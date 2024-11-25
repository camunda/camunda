/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.opensearch.client.opensearch.OpenSearchClient;

public abstract class SearchDBExtension
    implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

  public static final ProcessIndex PROCESS_INDEX =
      new ProcessIndex("test-" + UUID.randomUUID(), true);
  public static final String IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY =
      "camunda.it.opensearch.aws.url";

  public static SearchDBExtension create() {
    final var openSearchAwsInstanceUrl =
        Optional.ofNullable(System.getProperty(IT_OPENSEARCH_AWS_INSTANCE_URL_PROPERTY)).orElse("");
    if (openSearchAwsInstanceUrl.isEmpty()) {
      return new ContainerizedSearchDBExtension();
    } else {
      return new AWSSearchDBExtension(openSearchAwsInstanceUrl);
    }
  }

  public abstract ElasticsearchClient esClient();

  public abstract OpenSearchClient osClient();
}
