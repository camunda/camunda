/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.NoopExporterConfiguration.ElasticsearchConfig;
import io.camunda.exporter.schema.ElasticsearchEngineClient;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public final class ElasticsearchClientFactory {
  public static ElasticsearchEngineClient createClient(final ElasticsearchConfig config) {
    // Create the low-level client
    final RestClient restClient = RestClient.builder(HttpHost.create(config.url)).build();

    // Create the transport with a Jackson mapper
    final ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());

    final ElasticsearchClient client = new ElasticsearchClient(transport);

    // And create the API client
    return new ElasticsearchEngineClient(client);
  }
}
