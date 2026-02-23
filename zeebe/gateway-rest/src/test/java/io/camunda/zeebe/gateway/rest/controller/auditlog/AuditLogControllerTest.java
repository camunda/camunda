/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.auditlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mapping.http.converters.AuditLogCategoryConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogEntityTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogOperationTypeConverter;
import io.camunda.gateway.mapping.http.converters.AuditLogResultConverter;
import io.camunda.gateway.protocol.model.AuditLogCategoryEnum;
import io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogResultEnum;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.AuditLogSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AuditLogServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(AuditLogController.class)
public class AuditLogControllerTest extends RestControllerTest {

  static final String AUDIT_LOGS_BASE_URL = "/v2/audit-logs";
  public static final String AUDIT_LOGS_SEARCH_URL = AUDIT_LOGS_BASE_URL + "/search";

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
        "items": [
          {
            "auditLogKey": "123",
            "entityKey": "entityKey",
            "entityType": "USER",
            "operationType": "CREATE",
            "batchOperationKey": "456",
            "batchOperationType": "ADD_VARIABLE",
            "timestamp": "2024-01-01T00:00:00.000Z",
            "actorId": "actor",
            "actorType": "USER",
            "tenantId": "tenant",
            "result": "SUCCESS",
            "annotation": "annotation",
            "category": "DEPLOYED_RESOURCES",
            "processDefinitionId": "processDefinitionId",
            "processDefinitionKey": "789",
            "processInstanceKey": "987",
            "elementInstanceKey": "654",
            "jobKey": "321",
            "userTaskKey": "111",
            "decisionRequirementsId": "drg-1",
            "decisionRequirementsKey": "222",
            "decisionDefinitionId": "decisionDefId",
            "decisionDefinitionKey": "333",
            "decisionEvaluationKey": "444",
            "agentElementId": null,
            "deploymentKey": null,
            "entityDescription": null,
            "formKey": null,
            "relatedEntityKey": null,
            "relatedEntityType": null,
            "resourceKey": null,
            "rootProcessInstanceKey": null
          }
        ],
        "page": {
          "totalItems": 1,
          "startCursor": null,
          "endCursor": null,
          "hasMoreTotalItems": false
        }
      }
      """;

  private static final String EXPECTED_GET_BY_KEY_RESPONSE =
      """
      {
        "auditLogKey": "123",
        "entityKey": "entityKey",
        "entityType": "USER",
        "operationType": "CREATE",
        "batchOperationKey": "456",
        "batchOperationType": "ADD_VARIABLE",
        "timestamp": "2024-01-01T00:00:00.000Z",
        "actorId": "actor",
        "actorType": "USER",
        "tenantId": "tenant",
        "result": "SUCCESS",
        "annotation": "annotation",
        "category": "DEPLOYED_RESOURCES",
        "processDefinitionId": "processDefinitionId",
        "processDefinitionKey": "789",
        "processInstanceKey": "987",
        "elementInstanceKey": "654",
        "jobKey": "321",
        "userTaskKey": "111",
        "decisionRequirementsId": "drg-1",
        "decisionRequirementsKey": "222",
        "decisionDefinitionId": "decisionDefId",
        "decisionDefinitionKey": "333",
        "decisionEvaluationKey": "444",
        "agentElementId": null,
        "deploymentKey": null,
        "entityDescription": null,
        "formKey": null,
        "relatedEntityKey": null,
        "relatedEntityType": null,
        "resourceKey": null,
        "rootProcessInstanceKey": null
      }
      """;

  private static final AuditLogEntity AUDIT_LOG_ENTITY =
      new AuditLogEntity.Builder()
          .auditLogKey("123")
          .entityKey("entityKey")
          .entityType(AuditLogEntity.AuditLogEntityType.USER)
          .operationType(AuditLogEntity.AuditLogOperationType.CREATE)
          .batchOperationKey(456L)
          .batchOperationType(BatchOperationType.ADD_VARIABLE)
          .timestamp(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
          .actorId("actor")
          .actorType(AuditLogEntity.AuditLogActorType.USER)
          .tenantId("tenant")
          .result(AuditLogEntity.AuditLogOperationResult.SUCCESS)
          .annotation("annotation")
          .category(AuditLogOperationCategory.DEPLOYED_RESOURCES)
          .processDefinitionId("processDefinitionId")
          .processDefinitionKey(789L)
          .processInstanceKey(987L)
          .elementInstanceKey(654L)
          .jobKey(321L)
          .userTaskKey(111L)
          .decisionRequirementsId("drg-1")
          .decisionRequirementsKey(222L)
          .decisionDefinitionId("decisionDefId")
          .decisionDefinitionKey(333L)
          .decisionEvaluationKey(444L)
          .build();

  @MockitoBean AuditLogServices auditLogServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(auditLogServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(auditLogServices);
  }

  @Test
  void shouldSearchAuditLogsWithEmptyBody() {
    // given
    final var searchResult = SearchQueryResult.of(AUDIT_LOG_ENTITY);
    when(auditLogServices.search(any(AuditLogQuery.class))).thenReturn(searchResult);

    // when/then
    webClient
        .post()
        .uri(AUDIT_LOGS_SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    final var captor = ArgumentCaptor.forClass(AuditLogQuery.class);
    verify(auditLogServices).search(captor.capture());
    assertThat(captor.getValue()).isNotNull();
  }

  @Test
  void shouldGetAuditLogByKey() {
    // given
    final var auditLogKey = "123";
    when(auditLogServices.getAuditLog(auditLogKey)).thenReturn(AUDIT_LOG_ENTITY);

    // when/then
    webClient
        .get()
        .uri(AUDIT_LOGS_BASE_URL + "/" + auditLogKey)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_GET_BY_KEY_RESPONSE, JsonCompareMode.STRICT);

    verify(auditLogServices).getAuditLog(auditLogKey);
  }

  @Test
  void shouldSearchAuditLogsWithFilter() {
    // given
    final var request =
        """
        {
            "filter": {
                "actorId": "actor",
                "actorType": "USER",
                "operationType": "CREATE",
                "entityType": "USER",
                "result": "SUCCESS",
                "category": "DEPLOYED_RESOURCES"
            }
        }
        """;
    final var searchResult = SearchQueryResult.of(AUDIT_LOG_ENTITY);
    when(auditLogServices.search(any(AuditLogQuery.class))).thenReturn(searchResult);

    // when/then
    webClient
        .post()
        .uri(AUDIT_LOGS_SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    final var filter =
        new AuditLogFilter.Builder()
            .actorIds("actor")
            .actorTypes(AuditLogActorType.USER.name())
            .operationTypes(
                AuditLogOperationTypeConverter.toInternalOperationTypeAsString(
                    AuditLogOperationTypeEnum.CREATE))
            .entityTypes(
                AuditLogEntityTypeConverter.toInternalEntityTypeAsString(
                    AuditLogEntityTypeEnum.USER))
            .categories(
                AuditLogCategoryConverter.toInternalCategoryAsString(
                    AuditLogCategoryEnum.DEPLOYED_RESOURCES))
            .results(AuditLogResultConverter.toInternalResultAsString(AuditLogResultEnum.SUCCESS))
            .build();
    verify(auditLogServices).search(new AuditLogQuery.Builder().filter(filter).build());
  }

  @Test
  void shouldSearchAuditLogsWithUnexpectedFilterEnumValue() {
    // given
    final var request =
        """
        {
            "filter": {
                "category": "SOMETHING"
            }
        }
        """;
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Unexpected value 'SOMETHING' for enum field 'category'. Use any of the following values: [ADMIN, DEPLOYED_RESOURCES, USER_TASKS]",
                  "instance": "%s"
                }""",
            AUDIT_LOGS_SEARCH_URL);

    // when/then
    webClient
        .post()
        .uri(AUDIT_LOGS_SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(auditLogServices, never()).search(any(AuditLogQuery.class));
  }

  @Test
  void shouldSearchAuditLogsWithSorting() {
    // given
    final var request =
        """
        {
            "sort": [
                {
                    "field": "actorId",
                    "order": "DESC"
                },
                {
                    "field": "decisionDefinitionKey",
                    "order": "ASC"
                }
            ]
        }
        """;
    final var searchResult = SearchQueryResult.of(AUDIT_LOG_ENTITY);
    when(auditLogServices.search(any(AuditLogQuery.class))).thenReturn(searchResult);

    // when/then
    webClient
        .post()
        .uri(AUDIT_LOGS_SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(auditLogServices)
        .search(
            new AuditLogQuery.Builder()
                .sort(
                    new AuditLogSort.Builder()
                        .actorId()
                        .desc()
                        .decisionDefinitionKey()
                        .asc()
                        .build())
                .build());
  }

  @Test
  void shouldSearchAuditLogsWithBadSortField() {
    // given
    final var request =
        """
        {
            "sort": [
                {
                    "field": "operationKey",
                    "order": "DESC"
                }
            ]
        }
        """;
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Unexpected value 'operationKey' for enum field 'field'. Use any of the following values: [actorId, actorType, annotation, auditLogKey, batchOperationKey, batchOperationType, category, decisionDefinitionId, decisionDefinitionKey, decisionEvaluationKey, decisionRequirementsId, decisionRequirementsKey, elementInstanceKey, entityKey, entityType, jobKey, operationType, processDefinitionId, processDefinitionKey, processInstanceKey, result, tenantId, timestamp, userTaskKey]",
                  "instance": "%s"
                }""",
            AUDIT_LOGS_SEARCH_URL);

    // when/then
    webClient
        .post()
        .uri(AUDIT_LOGS_SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(auditLogServices, never()).search(any(AuditLogQuery.class));
  }
}
