/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.camunda.search.security.auth.Authentication;
import io.camunda.service.IncidentServices;
import io.camunda.service.exception.CamundaBrokerException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(IncidentController.class)
public class IncidentControllerTest extends RestControllerTest {

  static final String INCIDENT_BASE_URL = "/v2/incidents";

  @MockBean IncidentServices incidentServices;

  @BeforeEach
  void setUp() {
    when(incidentServices.withAuthentication(any(Authentication.class)))
        .thenReturn(incidentServices);
  }

  @Test
  void shouldResolveIncident() {
    // given
    when(incidentServices.resolveIncident(anyLong()))
        .thenReturn(CompletableFuture.completedFuture(new IncidentRecord()));

    // when/then
    webClient
        .post()
        .uri(INCIDENT_BASE_URL + "/1/resolution")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(incidentServices).resolveIncident(1L);
  }

  @Test
  void shouldReturnNotFoundIfIncidentNotFound() {
    // given
    Mockito.when(incidentServices.resolveIncident(anyLong()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CamundaBrokerException(
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
        .json(expectedBody);

    Mockito.verify(incidentServices).resolveIncident(1L);
  }
}
