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

import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.JobServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = JobController.class)
public class JobQueryControllerTest extends RestControllerTest {

  static final String JOB_URL = "/v2/jobs/";
  static final String JOB_SEARCH_URL = JOB_URL + "search";
  static final String EXPECTED_SEARCH_RESPONSE =
      """
        {
            "items": [
                {
                    "jobKey": "1",
                    "type": "testJob",
                    "worker": "testWorker",
                    "state": "COMPLETED",
                    "kind": "TASK_LISTENER",
                    "listenerEventType": "COMPLETING",
                    "retries": 3,
                    "isDenied": true,
                    "deniedReason": "test denied reason",
                    "hasFailedWithRetriesLeft": false,
                    "errorCode": "123",
                    "errorMessage": "test error message",
                    "customHeaders": {
                        "foo": "bar"
                    },
                    "deadline": "2025-06-05T09:05:00.000Z",
                    "endTime": "2025-06-05T10:05:00.000Z",
                    "processDefinitionId": "processDefinitionId",
                    "processDefinitionKey": "2",
                    "processInstanceKey": "3",
                    "rootProcessInstanceKey": "37",
                    "elementId": "elementId",
                    "elementInstanceKey": "4",
                    "tenantId": "<default>",
                    "creationTime": "2025-06-05T09:00:00.000Z",
                    "lastUpdateTime": "2025-06-05T10:04:00.000Z"
                }
            ],
            "page": {
                "totalItems": 1,
                "startCursor": "123base64",
                "endCursor": "456base64",
                "hasMoreTotalItems": false
            }
        }
        """;
  private static final SearchQueryResult<JobEntity> SEARCH_QUERY_RESULT =
      new SearchQueryResult.Builder<JobEntity>()
          .total(1L)
          .items(
              List.of(
                  new JobEntity.Builder()
                      .jobKey(1L)
                      .type("testJob")
                      .worker("testWorker")
                      .state(JobState.COMPLETED)
                      .kind(JobKind.TASK_LISTENER)
                      .listenerEventType(ListenerEventType.COMPLETING)
                      .retries(3)
                      .isDenied(true)
                      .deniedReason("test denied reason")
                      .hasFailedWithRetriesLeft(false)
                      .errorCode("123")
                      .errorMessage("test error message")
                      .customHeaders(Map.of("foo", "bar"))
                      .deadline(OffsetDateTime.parse("2025-06-05T09:05:00.000Z"))
                      .endTime(OffsetDateTime.parse("2025-06-05T10:05:00.000Z"))
                      .processDefinitionId("processDefinitionId")
                      .processDefinitionKey(2L)
                      .processInstanceKey(3L)
                      .rootProcessInstanceKey(37L)
                      .elementId("elementId")
                      .elementInstanceKey(4L)
                      .tenantId("<default>")
                      .creationTime(OffsetDateTime.parse("2025-06-05T09:00:00.000Z"))
                      .lastUpdateTime(OffsetDateTime.parse("2025-06-05T10:04:00.000Z"))
                      .build()))
          .startCursor("123base64")
          .endCursor("456base64")
          .build();
  @MockitoBean JobServices<JobActivationResult> jobServices;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean ResponseObserverProvider responseObserverProvider;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupJobServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(jobServices.withAuthentication(ArgumentMatchers.any(CamundaAuthentication.class)))
        .thenReturn(jobServices);
  }

  @Test
  void shouldSearchJobWithEmptyBody() {
    // given
    when(jobServices.search(any(JobQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(JOB_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(jobServices).search(new JobQuery.Builder().build());
  }

  @Test
  void shouldSearchJobWithAllFilters() {
    // given
    when(jobServices.search(any(JobQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

    final var request =
        """
      {
      "filter": {
        "deadline": "2025-06-05T09:05:00.000Z",
        "deniedReason": "test denied reason",
        "elementId": "elementId",
        "elementInstanceKey": "4",
        "endTime": "2025-06-05T10:05:00.000Z",
        "errorCode": "123",
        "errorMessage": "test error message",
        "hasFailedWithRetriesLeft": false,
        "isDenied": true,
        "jobKey": "1",
        "kind": "TASK_LISTENER",
        "listenerEventType": "COMPLETING",
        "processDefinitionId": "processDefinitionId",
        "processDefinitionKey": "2",
        "processInstanceKey": "3",
        "retries": 3,
        "state": "COMPLETED",
        "tenantId": "<default>",
        "type": "testJob",
        "worker": "testWorker",
        "creationTime": "2025-06-05T09:00:00.000Z",
        "lastUpdateTime": "2025-06-05T10:04:00.000Z"
          }
        }
    """;

    // when / then
    webClient
        .post()
        .uri(JOB_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(jobServices)
        .search(
            new JobQuery.Builder()
                .filter(
                    new JobFilter.Builder()
                        .deniedReasons("test denied reason")
                        .deadlines(OffsetDateTime.parse("2025-06-05T09:05:00.000Z"))
                        .elementIds("elementId")
                        .elementInstanceKeys(4L)
                        .endTimes(OffsetDateTime.parse("2025-06-05T10:05:00.000Z"))
                        .errorCodes("123")
                        .errorMessages("test error message")
                        .hasFailedWithRetriesLeft(false)
                        .isDenied(true)
                        .jobKeys(1L)
                        .kinds(JobEntity.JobKind.TASK_LISTENER.name())
                        .listenerEventTypes(JobEntity.ListenerEventType.COMPLETING.name())
                        .processDefinitionIds("processDefinitionId")
                        .processDefinitionKeys(2L)
                        .processInstanceKeys(3L)
                        .retries(3)
                        .states(JobEntity.JobState.COMPLETED.name())
                        .tenantIds("<default>")
                        .types("testJob")
                        .workers("testWorker")
                        .creationTimes(OffsetDateTime.parse("2025-06-05T09:00:00.000Z"))
                        .lastUpdateTimes(OffsetDateTime.parse("2025-06-05T10:04:00.000Z"))
                        .build())
                .build());
  }

  @Test
  void shouldSearchJobWithFullSorting() {
    // given
    when(jobServices.search(any(JobQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

    final var request =
        """
  {
    "sort": [
      { "field": "jobKey", "order": "asc" },
      { "field": "type", "order": "desc" },
      { "field": "worker", "order": "asc" },
      { "field": "state", "order": "desc" },
      { "field": "kind", "order": "asc" },
      { "field": "listenerEventType", "order": "desc" },
      { "field": "retries", "order": "asc" },
      { "field": "isDenied", "order": "desc" },
      { "field": "deniedReason", "order": "asc" },
      { "field": "hasFailedWithRetriesLeft", "order": "desc" },
      { "field": "errorCode", "order": "asc" },
      { "field": "errorMessage", "order": "desc" },
      { "field": "deadline", "order": "asc" },
      { "field": "endTime", "order": "desc" },
      { "field": "processDefinitionId", "order": "asc" },
      { "field": "processDefinitionKey", "order": "desc" },
      { "field": "processInstanceKey", "order": "asc" },
      { "field": "elementId", "order": "desc" },
      { "field": "elementInstanceKey", "order": "asc" },
      { "field": "tenantId", "order": "desc" }
    ]
  }
  """;

    // when / then
    webClient
        .post()
        .uri(JOB_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(jobServices)
        .search(
            new JobQuery.Builder()
                .sort(
                    b ->
                        b.jobKey()
                            .asc()
                            .type()
                            .desc()
                            .worker()
                            .asc()
                            .state()
                            .desc()
                            .jobKind()
                            .asc()
                            .listenerEventType()
                            .desc()
                            .retries()
                            .asc()
                            .isDenied()
                            .desc()
                            .deniedReason()
                            .asc()
                            .hasFailedWithRetriesLeft()
                            .desc()
                            .errorCode()
                            .asc()
                            .errorMessage()
                            .desc()
                            .deadline()
                            .asc()
                            .endTime()
                            .desc()
                            .processDefinitionId()
                            .asc()
                            .processDefinitionKey()
                            .desc()
                            .processInstanceKey()
                            .asc()
                            .elementId()
                            .desc()
                            .elementInstanceKey()
                            .asc()
                            .tenantId()
                            .desc())
                .build());
  }
}
