/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.mock.mockito.MockBean;

abstract class RestApiConfigurationTest extends RestControllerTest {

  static final String PROCESS_INSTANCES_SEARCH_URL = "/v2/process-instances/search";
  static final String TOPOLOGY_URL = "/v2/topology";

  @MockBean ProcessInstanceServices processInstanceServices;
  @MockBean BrokerClient brokerClient;
  @MockBean BrokerTopologyManager topologyManager;

  @BeforeEach
  void setupServices() {
    when(processInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(processInstanceServices);
    when(brokerClient.getTopologyManager()).thenReturn(topologyManager);
    when(processInstanceServices.search(any(ProcessInstanceQuery.class)))
        .thenReturn(new Builder<ProcessInstanceEntity>().build());
    when(topologyManager.getTopology()).thenReturn(mock(BrokerClusterState.class));
  }
}
