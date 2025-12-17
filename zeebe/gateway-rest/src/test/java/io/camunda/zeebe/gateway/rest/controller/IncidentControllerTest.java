/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.IncidentProcessInstanceStatisticsEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(IncidentController.class)
public class IncidentControllerTest extends RestControllerTest {

  static final String INCIDENT_BASE_URL = "/v2/incidents";
  static final String INCIDENT_SEARCH_URL = INCIDENT_BASE_URL + "/search";
  static final String INCIDENT_PROCESS_INSTANCE_STATISTICS_URL =
      INCIDENT_BASE_URL + "/statistics/process-instances";
  private static final SearchQueryResult<IncidentProcessInstanceStatisticsEntity>
      INCIDENT_STATISTICS_RESULT =
          new SearchQueryResult.Builder<IncidentProcessInstanceStatisticsEntity>()
              .total(1L)
              .items(List.of(new IncidentProcessInstanceStatisticsEntity("hash", "error", 10L)))
              .startCursor(null)
              .endCursor(null)
              .build();
  @MockitoBean IncidentServices incidentServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(incidentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(incidentServices);
  }

  @Test
  void shouldResolveIncident() {
    // given
    when(incidentServices.resolveIncident(anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new IncidentRecord()));

    final String request =
        """
            {
              "operationReference": 12345678
            }""";

    // when/then
    webClient
        .post()
        .uri(INCIDENT_BASE_URL + "/1/resolution")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(incidentServices).resolveIncident(1L, 12345678L);
  }

  @Test
  void shouldReturnNotFoundIfIncidentNotFound() {
    // given
    Mockito.when(incidentServices.resolveIncident(anyLong(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        IncidentIntent.RESOLVE,
                        1L,
                        RejectionType.NOT_FOUND,
                        "Incident not found"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 404,
              "title": "NOT_FOUND",
              "detail": "Command 'RESOLVE' rejected with code 'NOT_FOUND': Incident not found",
              "instance": "%s"
            }"""
            .formatted(INCIDENT_BASE_URL + "/1/resolution");

    // when / then
    webClient
        .post()
        .uri(INCIDENT_BASE_URL + "/1/resolution")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(incidentServices).resolveIncident(1L, null);
  }

  @Test
  void shouldReturnIncidentProcessInstanceStatistics() {
    when(incidentServices.incidentProcessInstanceStatistics(
            any(IncidentProcessInstanceStatisticsQuery.class)))
        .thenReturn(INCIDENT_STATISTICS_RESULT);

    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            """
                {
                  "items": [
                    {
                      "errorHashCode": "hash",
                      "errorMessage": "error",
                      "activeInstancesWithErrorCount": 10
                    }
                  ],
                  "page": {
                    "totalItems": 1,
                    "hasMoreTotalItems": false
                  }
                }
                """,
            JsonCompareMode.STRICT);

    final var result = new IncidentProcessInstanceStatisticsQuery.Builder().build();
    final ArgumentCaptor<IncidentProcessInstanceStatisticsQuery> captor =
        ArgumentCaptor.forClass(IncidentProcessInstanceStatisticsQuery.class);
    verify(incidentServices).incidentProcessInstanceStatistics(captor.capture());
    assertThat(captor.getValue()).isEqualTo(result);
  }
}
