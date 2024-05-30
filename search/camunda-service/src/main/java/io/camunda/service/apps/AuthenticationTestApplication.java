/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.apps;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.data.clients.ElasticsearchDataStoreClient;
import io.camunda.service.CamundaServices;
import io.camunda.service.query.search.SearchQueryBuilders;
import java.util.List;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public class AuthenticationTestApplication {

  public static void main(String[] args) throws Exception {
    // create Elasticsearch Client
    final var serverUrl = "http://localhost:9200";
    final var restClient = RestClient.builder(HttpHost.create(serverUrl)).build();
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    final var esClient = new ElasticsearchClient(transport);
    //
    final var dataStoreClient = new ElasticsearchDataStoreClient(esClient);
    final var camundaServices = new CamundaServices(dataStoreClient);

    // create search query
    final var searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery(
            (q) ->
                q.filter(
                        (f) ->
                            f.processInstanceKeys(4503599627370497L)
                                .variable((v) -> v.name("foo").gt(10).lt(100)))
                    .sort((s) -> s.endDate().asc())
                    .page((p) -> p.searchAfter(List.of("foo", "bar").toArray())));

    // Authentication: Execute search query with an authenticated user
    camundaServices
        .withAuthentication(
            (a) ->
                a.user("foo").groups(List.of("bar", "oof")).tenants(List.of("tenant1", "tenant2")))
        .processInstanceServices()
        .search(searchQuery);

    // ... or with another authenticated user
    camundaServices
        .withAuthentication(
            (a) ->
                a.user("bar")
                    .groups(List.of("foo", "oof"))
                    .tenants(List.of("tenant10", "tenant20")))
        .processInstanceServices()
        .search(searchQuery);
  }
}
