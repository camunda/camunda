/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

// import co.elastic.clients.elasticsearch.ElasticsearchClient;
// import co.elastic.clients.json.jackson.JacksonJsonpMapper;
// import co.elastic.clients.transport.rest_client.RestClientTransport;

public class TestApplication {

  //  public static void main(String[] args) throws Exception {
  //    //    // create Elasticsearch Client
  //    //    final var serverUrl = "http://localhost:9200";
  //    //    final var restClient = RestClient.builder(HttpHost.create(serverUrl)).build();
  //    //    final var transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
  //    //    final var esClient = new ElasticsearchClient(transport);
  //
  //    // create Opensearch Client
  //    final var serverUrl = "http://localhost:9200";
  //    final var httpHost = HttpHost.create(serverUrl);
  //    final var transport =
  //        ApacheHttpClient5TransportBuilder.builder(httpHost)
  //            .setMapper(new WrappedJsonMapper(new JacksonJsonpMapper()))
  //            .build();
  //    final var client = new OpenSearchClient(transport);
  //
  //    // create/configure Camunda Services
  //    //    final var dataStoreClient = new ElasticsearchDataStoreClient(esClient);
  //    final var dataStoreClient = new OpensearchDataStoreClient(client);
  //    final var camundaServices = new CamundaServices(dataStoreClient);
  //
  //    // start process instance
  //    // camundaServices.processInstanceService()
  //    // .createProcessInstance()
  //    // .bpmnProcessId("foo")
  //    // .execute();
  //
  //    final var variableFilter = FilterBuilders.variable((f) ->
  // f.name("abc").gt(123456).lt(78979));
  //
  //    final var filter =
  //        FilterBuilders.processInstance(
  //            (f) -> f.processInstanceKeys(4503599627370497L).variable(variableFilter));
  //
  //    // execute process instance search query
  //    final var foo =
  //        camundaServices
  //            .processInstanceServices()
  //            .search(
  //                (b) ->
  //                    b.filter(filter)
  //                        .sort((s) -> s.field("endDate").asc())
  //                        .page((p) -> p.from(0).size(20)));
  //
  //    final var sortValues = foo.sortValues();
  //    camundaServices
  //        .processInstanceServices()
  //        .search(
  //            (b) ->
  //                b.filter(filter)
  //                    .sort((s) -> s.field("endDate").asc())
  //                    .page((p) -> p.searchAfter(sortValues)));
  //    System.out.println("fop");
  //  }
}
