/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.search.entities.DocumentReferenceEntity;
import io.camunda.search.query.DocumentReferenceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.DocumentReferenceServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = DocumentReferenceController.class)
public class DocumentReferenceControllerTest extends RestControllerTest {

  private static final String SEARCH_URL = "/v2/document-references/search";

  private static final DocumentReferenceEntity SAMPLE_DOC_REF =
      new DocumentReferenceEntity(
          100L,
          "myDoc",
          200L,
          300L,
          400L,
          "invoice-process",
          null,
          "<default>",
          "doc123",
          "aws",
          "invoice.pdf",
          "application/pdf",
          1024L,
          "2025-12-31T00:00:00Z",
          "hash1",
          null);

  private static final SearchQueryResult<DocumentReferenceEntity> SEARCH_QUERY_RESULT =
      new Builder<DocumentReferenceEntity>()
          .total(1L)
          .items(List.of(SAMPLE_DOC_REF))
          .startCursor("0")
          .endCursor("0")
          .build();

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
            "items": [
              {
                "variableKey": "100",
                "variableName": "myDoc",
                "scopeKey": "200",
                "processInstanceKey": "300",
                "processDefinitionKey": "400",
                "processDefinitionId": "invoice-process",
                "tenantId": "<default>",
                "documentId": "doc123",
                "storeId": "aws",
                "fileName": "invoice.pdf",
                "contentType": "application/pdf",
                "size": 1024,
                "expiresAt": "2025-12-31T00:00:00Z",
                "contentHash": "hash1"
              }
            ],
            "page": {
              "totalItems": 1,
              "startCursor": "0",
              "endCursor": "0",
              "hasMoreTotalItems": false
            }
          }
          """;

  @MockitoBean DocumentReferenceServices documentReferenceServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(documentReferenceServices.search(any(DocumentReferenceQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);
  }

  @Test
  void shouldSearchDocumentReferences() {
    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.LENIENT);

    verify(documentReferenceServices)
        .search(eq(new DocumentReferenceQuery.Builder().build()), any());
  }

  @Test
  void shouldSearchDocumentReferencesWithFilter() {
    // given
    final var request =
        """
            {
              "filter": {
                "processInstanceKey": "300"
              }
            }
            """;

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.LENIENT);
  }

  @Test
  void shouldReturnEmptyResultOnSearch() {
    // given
    when(documentReferenceServices.search(any(DocumentReferenceQuery.class), any()))
        .thenReturn(new Builder<DocumentReferenceEntity>().total(0L).items(List.of()).build());

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items")
        .isArray()
        .jsonPath("$.items.length()")
        .isEqualTo(0);
  }

  @Test
  void shouldReturn400OnInvalidFilter() {
    // given - an invalid processInstanceKey (not a valid long)
    final var request =
        """
            {
              "filter": {
                "processInstanceKey": {
                  "$gt": "not-a-number"
                }
              }
            }
            """;

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
