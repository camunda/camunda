/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.services;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.data.clients.ElasticsearchDataStoreClient;
import io.camunda.data.clients.OpensearchDataStoreClient;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.service.CamundaServices;
import io.camunda.service.ProcessInstanceServices;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamundaServicesConfiguration {

  @Bean
  @Conditional(ElasticsearchCondition.class)
  public CamundaServices elasticsearchCamundaServices(final ElasticsearchClient esClient) {
    return new CamundaServices(new ElasticsearchDataStoreClient(esClient));
  }

  @Bean
  @Conditional(OpensearchCondition.class)
  public CamundaServices opensearchCamundaServices(final OpenSearchClient openSearchClient) {
    return new CamundaServices(new OpensearchDataStoreClient(openSearchClient));
  }

  @Bean
  public ProcessInstanceServices processInstancesServices(final CamundaServices camundaServices) {
    return camundaServices.processInstanceServices();
  }
}
