/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.service.IncidentServices;
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.search.query.IncidentQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.SearchQueryResult.Builder;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = IncidentQueryController.class, properties = "camunda.rest.query.enabled=true")
public class IncidentQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
          "items": [
              {
                  "key": 5,
                  "processDefinitionKey": 23,
                  "processInstanceKey": 42,
                  "type": "type",
                  "flowNodeId": "flowNodeId",
                  "flowNodeInstanceId": "flowNodeInstanceId",
                  "creationTime": "2024",
                  "state": "state",
                  "jobKey": 5,
                  "tenantId": "tenantId",
                  "hasActiveOperation": false
              }
          ],
          "page": {
              "totalItems": 1,
              "firstSortValues": [],
              "lastSortValues": [
                  "v"
              ]
          }
      }""";

  static final SearchQueryResult<IncidentEntity> SEARCH_QUERY_RESULT =
      new Builder<IncidentEntity>()
          .total(1L)
          .items(
              List.of(
                  new IncidentEntity(
                      5L,
                      23L,
                      42L,
                      "type",
                      "flowNodeId",
                      "flowNodeInstanceId",
                      "2024",
                      "state",
                      5L,
                      "tenantId",
                      false,
                      null, /*new OperationEntity(
                            "id", "batchOperationId", "type", "state", "errorMessage", "2024"),*/
                      null, /*new ProcessInstanceReference(
                            "instanceId", "processDefinitionId", "processDefinitionName"),*/
                      null /*new DecisionInstanceReference("instanceId", "decisionName")*/)))
          .sortValues(new Object[] {"v"})
          .build();

  static final String INCIDENT_SEARCH_URL = "/v2/incidents/search";

  @MockBean IncidentServices incidentServices;

  @BeforeEach
  void setIncidentServices() {
    when(incidentServices.withAuthentication(ArgumentMatchers.any(Authentication.class)))
        .thenReturn(incidentServices);
  }

  @Test
  void shouldSearchIncidentWithEmptyBody() {
    // given
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(INCIDENT_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(incidentServices).search(new IncidentQuery.Builder().build());
  }
}
