/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.factories;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.elasticsearch.client.RestHighLevelClient;

public class DatastoreClientHolder {

  private static final DatastoreClientHolder INSTANCE = new DatastoreClientHolder();
  private ElasticsearchClient elasticsearchClient;
  private RestHighLevelClient esClient;

  private DatastoreClientHolder() {}

  public static DatastoreClientHolder getInstance() {
    return INSTANCE;
  }

  public static void init(
      final ElasticsearchClient elasticsearchClient, final RestHighLevelClient esClient) {
    INSTANCE.elasticsearchClient = elasticsearchClient;
    INSTANCE.esClient = esClient;
  }

  public ElasticsearchClient getElasticsearchClient() {
    return elasticsearchClient;
  }

  public DatastoreClientHolder setElasticsearchClient(
      final ElasticsearchClient elasticsearchClient) {
    this.elasticsearchClient = elasticsearchClient;
    return this;
  }

  public RestHighLevelClient getEsClient() {
    return esClient;
  }

  public DatastoreClientHolder setRestHighLevelClient(final RestHighLevelClient esClient) {
    this.esClient = esClient;
    return this;
  }
}
