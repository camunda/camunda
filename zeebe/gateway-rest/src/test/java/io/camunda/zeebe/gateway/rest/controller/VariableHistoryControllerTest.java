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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.protocol.model.VariableHistoryItemVariableOperationTypeEnum;
import io.camunda.search.entities.VariableHistoryItemEntity;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemActorType;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemOperationType;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemVariableOperationType;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.VariableHistoryItemFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableHistoryItemQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.VariableHistoryServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(VariableHistoryController.class)
public class VariableHistoryControllerTest extends RestControllerTest {

  private static final String SEARCH_URL = "/v2/variables/history/search";
  private static final String PREVIEW = "{\"truncated\":";
  private static final String FULL_VALUE = "{\"truncated\":\"no longer\"}";

  private static final String EXPECTED_ITEM_TEMPLATE =
      """
      {
        "variableHistoryItemKey": "1",
        "variableKey": "2",
        "name": "orderTotal",
        "value": %s,
        "isTruncated": %s,
        "scopeKey": "3",
        "elementInstanceKey": %s,
        "elementId": %s,
        "processInstanceKey": "5",
        "rootProcessInstanceKey": %s,
        "processDefinitionKey": "7",
        "processDefinitionId": "process",
        "tenantId": "<default>",
        "timestamp": "2024-01-01T00:00:00.000Z",
        "operationType": "CREATE",
        "variableOperationType": "API",
        "actorType": "USER",
        "actorId": %s,
        "agentElementId": %s
      }
      """;

  private static final String EXPECTED_PAGE =
      """
      {
        "totalItems": 1,
        "startCursor": null,
        "endCursor": null,
        "hasMoreTotalItems": false
      }
      """;

  @MockitoBean VariableHistoryServices variableHistoryServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setUp() {
    when(serviceRegistry.variableHistoryServices(any())).thenReturn(variableHistoryServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldSearchWithEmptyBody() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            expectedResponse(
                expectedItem(
                    "\"\\\"v\\\"\"",
                    false,
                    "\"4\"",
                    "\"task1\"",
                    "\"6\"",
                    "\"demo\"",
                    "\"agent\"")),
            JsonCompareMode.STRICT);

    verify(variableHistoryServices).search(eq(VariableHistoryItemQuery.of(q -> q)), any());
  }

  @Test
  void shouldSearchWithEmptyJsonObject() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk();

    verify(variableHistoryServices).search(eq(VariableHistoryItemQuery.of(q -> q)), any());
  }

  @Test
  void shouldReturnNullableFieldsAsNull() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, false)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            expectedResponse(
                expectedItem("\"\\\"v\\\"\"", false, "null", "null", "null", "null", "null")),
            JsonCompareMode.STRICT);
  }

  static Stream<Arguments> provideTruncateValuesCases() {
    final var preview = "\"" + PREVIEW.replace("\"", "\\\"") + "\"";
    final var full = "\"" + FULL_VALUE.replace("\"", "\\\"") + "\"";
    return Stream.of(
        // query, isPreview, fullValue, expected value, expected isTruncated
        Arguments.of("", false, null, preview, false),
        Arguments.of("", true, FULL_VALUE, preview, true),
        Arguments.of("", true, null, preview, true),
        Arguments.of("?truncateValues=true", true, FULL_VALUE, preview, true),
        Arguments.of("?truncateValues=true", true, null, preview, true),
        Arguments.of("?truncateValues=true", false, FULL_VALUE, preview, false),
        Arguments.of("?truncateValues=false", false, null, preview, false),
        Arguments.of("?truncateValues=false", true, FULL_VALUE, full, false),
        Arguments.of("?truncateValues=false", true, null, preview, true),
        Arguments.of("?truncateValues=false", false, FULL_VALUE, preview, false));
  }

  @ParameterizedTest
  @MethodSource("provideTruncateValuesCases")
  void shouldApplyTruncateValues(
      final String queryString,
      final boolean isPreview,
      final String fullValue,
      final String expectedValue,
      final boolean expectedIsTruncated) {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(isPreview, PREVIEW, fullValue, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL + queryString)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            expectedResponse(
                expectedItem(
                    expectedValue,
                    expectedIsTruncated,
                    "\"4\"",
                    "\"task1\"",
                    "\"6\"",
                    "\"demo\"",
                    "\"agent\"")),
            JsonCompareMode.STRICT);
  }

  static Stream<Arguments> provideFilterParameters() {
    final var streamBuilder = Stream.<Arguments>builder();
    keyOperationTestCases(
        streamBuilder,
        "variableKey",
        ops -> new VariableHistoryItemFilter.Builder().variableKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "processInstanceKey",
        ops -> new VariableHistoryItemFilter.Builder().processInstanceKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "scopeKey",
        ops -> new VariableHistoryItemFilter.Builder().scopeKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "elementInstanceKey",
        ops -> new VariableHistoryItemFilter.Builder().elementInstanceKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "variableHistoryItemKey",
        ops ->
            new VariableHistoryItemFilter.Builder().variableHistoryItemKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "processDefinitionKey",
        ops -> new VariableHistoryItemFilter.Builder().processDefinitionKeyOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "name",
        ops -> new VariableHistoryItemFilter.Builder().nameOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "tenantId",
        ops -> new VariableHistoryItemFilter.Builder().tenantIdOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "actorId",
        ops -> new VariableHistoryItemFilter.Builder().actorIdOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "elementId",
        ops -> new VariableHistoryItemFilter.Builder().elementIdOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "agentElementId",
        ops -> new VariableHistoryItemFilter.Builder().agentElementIdOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "processDefinitionId",
        ops -> new VariableHistoryItemFilter.Builder().processDefinitionIdOperations(ops).build());
    dateTimeOperationTestCases(
        streamBuilder,
        "timestamp",
        ops -> new VariableHistoryItemFilter.Builder().timestampOperations(ops).build());
    customOperationTestCases(
        streamBuilder,
        "operationType",
        ops -> new VariableHistoryItemFilter.Builder().operationTypeOperations(ops).build(),
        enumOperations("CREATE", "UPDATE"),
        true);
    customOperationTestCases(
        streamBuilder,
        "variableOperationType",
        ops -> new VariableHistoryItemFilter.Builder().variableOperationTypeOperations(ops).build(),
        enumOperations("API", "USER_TASK_COMPLETION"),
        true);
    customOperationTestCases(
        streamBuilder,
        "actorType",
        ops -> new VariableHistoryItemFilter.Builder().actorTypeOperations(ops).build(),
        enumOperations("USER", "CLIENT"),
        true);
    return streamBuilder.build();
  }

  private static List<List<Operation<String>>> enumOperations(
      final String first, final String second) {
    return List.of(
        List.of(Operation.eq(first)),
        List.of(Operation.neq(second)),
        List.of(Operation.in(first, second)),
        List.of(Operation.like(first.substring(0, 2) + "%")));
  }

  @ParameterizedTest
  @MethodSource("provideFilterParameters")
  void shouldSearchWithFilter(final String filterJson, final VariableHistoryItemFilter filter) {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"filter\": %s}".formatted(filterJson))
        .exchange()
        .expectStatus()
        .isOk();

    verify(variableHistoryServices)
        .search(eq(VariableHistoryItemQuery.of(q -> q.filter(filter))), any());
  }

  @Test
  void shouldRejectUnknownEnumFilterValue() {
    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"filter\": {\"operationType\": \"SOMETHING\"}}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo(
            "Unexpected value 'SOMETHING' for enum field 'operationType'. "
                + "Use any of the following values: [CREATE, UPDATE]");

    verify(variableHistoryServices, never()).search(any(VariableHistoryItemQuery.class), any());
  }

  @Test
  void shouldRejectMalformedKeyFilter() {
    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"filter\": {\"variableKey\": \"abc\"}}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo(
            "The provided variableKey 'abc' is not a valid key. Expected a numeric value. "
                + "Did you pass an entity id instead of an entity key?.");

    verify(variableHistoryServices, never()).search(any(VariableHistoryItemQuery.class), any());
  }

  @Test
  void shouldRejectUnparsableTimestampFilter() {
    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"filter\": {\"timestamp\": \"yesterday\"}}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo(
            "The provided timestamp 'yesterday' cannot be parsed as a date according to RFC 3339,"
                + " section 5.6.");

    verify(variableHistoryServices, never()).search(any(VariableHistoryItemQuery.class), any());
  }

  @Test
  void shouldSearchWithSorting() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sort\": [{\"field\": \"variableHistoryItemKey\", \"order\": \"DESC\"}]}")
        .exchange()
        .expectStatus()
        .isOk();

    verify(variableHistoryServices)
        .search(
            eq(VariableHistoryItemQuery.of(q -> q.sort(s -> s.variableHistoryItemKey().desc()))),
            any());
  }

  @Test
  void shouldSearchWithAscendingSortByDefault() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sort\": [{\"field\": \"variableHistoryItemKey\"}]}")
        .exchange()
        .expectStatus()
        .isOk();

    verify(variableHistoryServices)
        .search(
            eq(VariableHistoryItemQuery.of(q -> q.sort(s -> s.variableHistoryItemKey().asc()))),
            any());
  }

  @Test
  void shouldRejectUnknownSortField() {
    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sort\": [{\"field\": \"name\", \"order\": \"ASC\"}]}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo(
            "Unexpected value 'name' for enum field 'field'. "
                + "Use any of the following values: [variableHistoryItemKey]");

    verify(variableHistoryServices, never()).search(any(VariableHistoryItemQuery.class), any());
  }

  @Test
  void shouldRejectMissingSortField() {
    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"sort\": [{\"order\": \"ASC\"}]}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("Sort field must not be null.");

    verify(variableHistoryServices, never()).search(any(VariableHistoryItemQuery.class), any());
  }

  @Test
  void shouldSearchWithOffsetPagination() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"page\": {\"from\": 5, \"limit\": 10}}")
        .exchange()
        .expectStatus()
        .isOk();

    verify(variableHistoryServices)
        .search(eq(VariableHistoryItemQuery.of(q -> q.page(p -> p.from(5).size(10)))), any());
  }

  @Test
  void shouldSearchWithCursorPagination() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"page\": {\"after\": \"cursor\", \"limit\": 10}}")
        .exchange()
        .expectStatus()
        .isOk();

    verify(variableHistoryServices)
        .search(
            eq(VariableHistoryItemQuery.of(q -> q.page(p -> p.after("cursor").size(10)))), any());
  }

  @Test
  void shouldRejectConflictingPagination() {
    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"page\": {\"after\": \"a\", \"from\": 4}}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("Only one of [from, after, before] is allowed.");

    verify(variableHistoryServices, never()).search(any(VariableHistoryItemQuery.class), any());
  }

  @Test
  void shouldReturnBadRequestForUndecodableCursor() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenThrow(
            new ServiceException("Cannot decode pagination cursor 'x'", Status.INVALID_ARGUMENT));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"page\": {\"after\": \"x\"}}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("Cannot decode pagination cursor 'x'");
  }

  @Test
  void shouldReturnForbiddenWhenNotAuthorized() {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenThrow(
            new ServiceException(
                "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource"
                    + " 'PROCESS_DEFINITION'",
                Status.FORBIDDEN));

    // when / then
    webClient.post().uri(SEARCH_URL).exchange().expectStatus().isForbidden();
  }

  @ParameterizedTest
  @EnumSource(VariableHistoryItemVariableOperationTypeEnum.class)
  void shouldAcceptEveryVariableOperationTypeInTheSpec(
      final VariableHistoryItemVariableOperationTypeEnum operationType) {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity(false, "\"v\"", null, true)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"filter\": {\"variableOperationType\": \"%s\"}}".formatted(operationType.getValue()))
        .exchange()
        .expectStatus()
        .isOk();

    verify(variableHistoryServices)
        .search(
            eq(
                VariableHistoryItemQuery.of(
                    q ->
                        q.filter(
                            f ->
                                f.variableOperationTypeOperations(
                                    Operation.eq(operationType.getValue()))))),
            any());
  }

  @ParameterizedTest
  @EnumSource(VariableHistoryItemVariableOperationType.class)
  void shouldReturnEveryStoredVariableOperationTypeInTheSpec(
      final VariableHistoryItemVariableOperationType operationType) {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(
            SearchQueryResult.of(
                new VariableHistoryItemEntity(
                    1L,
                    2L,
                    "orderTotal",
                    "\"v\"",
                    null,
                    false,
                    3L,
                    null,
                    null,
                    5L,
                    null,
                    7L,
                    "process",
                    "<default>",
                    OffsetDateTime.parse("2024-01-01T00:00:00Z"),
                    VariableHistoryItemOperationType.CREATE,
                    operationType,
                    VariableHistoryItemActorType.USER,
                    null,
                    null)));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items[0].variableOperationType")
        .isEqualTo(operationType.name());
  }

  static Stream<Arguments> provideEveryActorAndOperationTypeConstant() {
    final var actorTypes =
        Arrays.stream(VariableHistoryItemActorType.values())
            .map(
                actorType ->
                    Arguments.of(
                        "actorType",
                        actorType.name(),
                        entityOf(VariableHistoryItemOperationType.CREATE, actorType),
                        new VariableHistoryItemFilter.Builder()
                            .actorTypeOperations(Operation.eq(actorType.name()))
                            .build()));
    final var operationTypes =
        Arrays.stream(VariableHistoryItemOperationType.values())
            .map(
                operationType ->
                    Arguments.of(
                        "operationType",
                        operationType.name(),
                        entityOf(operationType, VariableHistoryItemActorType.USER),
                        new VariableHistoryItemFilter.Builder()
                            .operationTypeOperations(Operation.eq(operationType.name()))
                            .build()));
    return Stream.concat(actorTypes, operationTypes);
  }

  @ParameterizedTest
  @MethodSource("provideEveryActorAndOperationTypeConstant")
  void shouldFilterByAndReturnEveryActorAndOperationTypeConstantInTheSpec(
      final String field,
      final String constant,
      final VariableHistoryItemEntity entity,
      final VariableHistoryItemFilter expectedFilter) {
    // given
    when(variableHistoryServices.search(any(VariableHistoryItemQuery.class), any()))
        .thenReturn(SearchQueryResult.of(entity));

    // when / then
    webClient
        .post()
        .uri(SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"filter\": {\"%s\": \"%s\"}}".formatted(field, constant))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.items[0]." + field)
        .isEqualTo(constant);

    verify(variableHistoryServices)
        .search(eq(VariableHistoryItemQuery.of(q -> q.filter(expectedFilter))), any());
  }

  private static VariableHistoryItemEntity entityOf(
      final VariableHistoryItemOperationType operationType,
      final VariableHistoryItemActorType actorType) {
    return new VariableHistoryItemEntity(
        1L,
        2L,
        "orderTotal",
        "\"v\"",
        null,
        false,
        3L,
        null,
        null,
        5L,
        null,
        7L,
        "process",
        "<default>",
        OffsetDateTime.parse("2024-01-01T00:00:00Z"),
        operationType,
        VariableHistoryItemVariableOperationType.API,
        actorType,
        null,
        null);
  }

  private static VariableHistoryItemEntity entity(
      final boolean isPreview,
      final String value,
      final String fullValue,
      final boolean withNullables) {
    return new VariableHistoryItemEntity(
        1L,
        2L,
        "orderTotal",
        value,
        fullValue,
        isPreview,
        3L,
        withNullables ? 4L : null,
        withNullables ? "task1" : null,
        5L,
        withNullables ? 6L : null,
        7L,
        "process",
        "<default>",
        OffsetDateTime.parse("2024-01-01T00:00:00Z"),
        VariableHistoryItemOperationType.CREATE,
        VariableHistoryItemVariableOperationType.API,
        VariableHistoryItemActorType.USER,
        withNullables ? "demo" : null,
        withNullables ? "agent" : null);
  }

  private static String expectedItem(
      final String value,
      final boolean isTruncated,
      final String elementInstanceKey,
      final String elementId,
      final String rootProcessInstanceKey,
      final String actorId,
      final String agentElementId) {
    return EXPECTED_ITEM_TEMPLATE.formatted(
        value,
        isTruncated,
        elementInstanceKey,
        elementId,
        rootProcessInstanceKey,
        actorId,
        agentElementId);
  }

  private static String expectedResponse(final String item) {
    return "{\"items\": [%s], \"page\": %s}".formatted(item, EXPECTED_PAGE);
  }
}
