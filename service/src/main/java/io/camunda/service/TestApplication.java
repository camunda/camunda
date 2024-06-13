/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.search.clients.ElasticsearchSearchClient;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.query.SearchQueryResult;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public class TestApplication {

  public static void main(final String[] args) {
    final var serverUrl = "http://localhost:9200";
    final var restClient = RestClient.builder(HttpHost.create(serverUrl)).build();
    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    final var esClient = new ElasticsearchClient(transport);

    final var dataStoreClient = new ElasticsearchSearchClient(esClient);
    final var camundaServices = new CamundaServices(dataStoreClient);

    final SearchQueryResult<ProcessInstanceEntity> result1 =
        camundaServices
            .processInstanceServices()
            .search(
                (q) ->
                    q.page((p) -> p.size(100))
                        .filter((p) -> p.processInstanceKeys(2251799813686164L))
                        .sort((s) -> s.processInstanceKey().desc()));

    result1.items().stream().forEach(System.out::println);

    final SearchQueryResult<UserTaskEntity> result =
        camundaServices
            .userTaskServices()
            .search(
                (q) ->
                    q.page((p) -> p.size(100))
                        .filter((p) -> p.taskStates("CREATED"))
                        .sort((s) -> s.startDate().desc()));

    result.items().stream().forEach(System.out::println);

    System.exit(-1);
  }
}
