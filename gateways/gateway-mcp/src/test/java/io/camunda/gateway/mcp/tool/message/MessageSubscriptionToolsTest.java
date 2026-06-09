/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.message;

import static io.camunda.gateway.mcp.tool.CallToolResultAssertions.assertTextContentFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mcp.OperationalToolsTest;
import io.camunda.gateway.protocol.model.MessageSubscriptionResult;
import io.camunda.gateway.protocol.model.MessageSubscriptionSearchQueryResult;
import io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum;
import io.camunda.gateway.protocol.model.MessageSubscriptionTypeEnum;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {MessageSubscriptionTools.class})
class MessageSubscriptionToolsTest extends OperationalToolsTest {

  static final MessageSubscriptionEntity MESSAGE_SUBSCRIPTION_ENTITY =
      MessageSubscriptionEntity.builder()
          .messageSubscriptionKey(5L)
          .processDefinitionId("complexProcess")
          .processDefinitionKey(23L)
          .processInstanceKey(42L)
          .rootProcessInstanceKey(42L)
          .flowNodeId("elementId")
          .flowNodeInstanceKey(17L)
          .messageSubscriptionState(MessageSubscriptionState.CREATED)
          .messageSubscriptionType(MessageSubscriptionType.PROCESS_EVENT)
          .dateTime(OffsetDateTime.parse("2024-05-23T23:05:00.000Z"))
          .messageName("myMessage")
          .correlationKey("myKey")
          .tenantId("<default>")
          .processDefinitionName("Complex Process")
          .processDefinitionVersion(2)
          .toolName("myTool")
          .inboundConnectorType("io.camunda:connector")
          .partitionId(1)
          .build();

  static final SearchQueryResult<MessageSubscriptionEntity> SEARCH_QUERY_RESULT =
      new Builder<MessageSubscriptionEntity>()
          .total(1L)
          .items(List.of(MESSAGE_SUBSCRIPTION_ENTITY))
          .startCursor("f")
          .endCursor("v")
          .build();

  @MockitoBean private MessageSubscriptionServices messageSubscriptionServices;

  @Autowired private JsonMapper objectMapper;
  @Captor private ArgumentCaptor<MessageSubscriptionQuery> queryCaptor;

  @BeforeEach
  void wireServiceRegistry() {
    when(serviceRegistry.messageSubscriptionServices(any()))
        .thenReturn(messageSubscriptionServices);
  }

  private void assertExampleMessageSubscription(final MessageSubscriptionResult item) {
    assertThat(item.getMessageSubscriptionKey()).isEqualTo("5");
    assertThat(item.getProcessDefinitionId()).isEqualTo("complexProcess");
    assertThat(item.getProcessDefinitionKey()).isEqualTo("23");
    assertThat(item.getProcessInstanceKey()).isEqualTo("42");
    assertThat(item.getElementId()).isEqualTo("elementId");
    assertThat(item.getElementInstanceKey()).isEqualTo("17");
    assertThat(item.getMessageSubscriptionState()).isEqualTo(MessageSubscriptionStateEnum.CREATED);
    assertThat(item.getMessageSubscriptionType())
        .isEqualTo(MessageSubscriptionTypeEnum.PROCESS_EVENT);
    assertThat(item.getMessageName()).isEqualTo("myMessage");
    assertThat(item.getCorrelationKey()).isEqualTo("myKey");
    assertThat(item.getProcessDefinitionName()).isEqualTo("Complex Process");
    assertThat(item.getProcessDefinitionVersion()).isEqualTo(2);
    assertThat(item.getToolName()).isEqualTo("myTool");
    assertThat(item.getInboundConnectorType()).isEqualTo("io.camunda:connector");
    assertThat(item.getPartitionId()).isEqualTo(1);
  }

  @Nested
  class SearchMessageSubscriptions {

    @Test
    void shouldSearchMessageSubscriptionsWithNoFilter() {
      // given
      when(messageSubscriptionServices.search(any(MessageSubscriptionQuery.class), any()))
          .thenReturn(SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder("searchMessageSubscriptions").arguments(Map.of()).build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var searchResult =
          objectMapper.convertValue(
              result.structuredContent(), MessageSubscriptionSearchQueryResult.class);
      assertThat(searchResult.getPage().getTotalItems()).isEqualTo(1L);
      assertThat(searchResult.getPage().getHasMoreTotalItems()).isFalse();
      assertThat(searchResult.getPage().getStartCursor()).isEqualTo("f");
      assertThat(searchResult.getPage().getEndCursor()).isEqualTo("v");
      assertThat(searchResult.getItems())
          .hasSize(1)
          .first()
          .satisfies(MessageSubscriptionToolsTest.this::assertExampleMessageSubscription);

      // partition 1 always injected
      verify(messageSubscriptionServices).search(queryCaptor.capture(), any());
      assertThat(queryCaptor.getValue().filter().partitionIdOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, 1));

      assertTextContentFallback(result);
    }

    @Test
    void shouldSearchMessageSubscriptionsWithFilterSortAndPaging() {
      // given
      when(messageSubscriptionServices.search(any(MessageSubscriptionQuery.class), any()))
          .thenReturn(SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder("searchMessageSubscriptions")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "messageSubscriptionState", "CREATED",
                              "messageName", "myMessage"),
                          "sort",
                          List.of(Map.of("field", "messageSubscriptionKey", "order", "DESC")),
                          "page",
                          Map.of("limit", 10, "after", "WzEwMjRd")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(messageSubscriptionServices).search(queryCaptor.capture(), any());
      final MessageSubscriptionQuery capturedQuery = queryCaptor.getValue();

      final MessageSubscriptionFilter filter = capturedQuery.filter();
      assertThat(filter.messageSubscriptionStateOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, "CREATED"));
      assertThat(filter.messageNameOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, "myMessage"));

      // partition 1 always injected
      assertThat(filter.partitionIdOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, 1));

      assertThat(capturedQuery.sort().orderings())
          .extracting(FieldSorting::field, FieldSorting::order)
          .containsExactly(tuple("messageSubscriptionKey", SortOrder.DESC));

      assertThat(capturedQuery.page().size()).isEqualTo(10);
      assertThat(capturedQuery.page().after()).isEqualTo("WzEwMjRd");

      assertTextContentFallback(result);
    }

    @Test
    void shouldSearchMessageSubscriptionsWithLastUpdatedDateRangeFilter() {
      // given
      when(messageSubscriptionServices.search(any(MessageSubscriptionQuery.class), any()))
          .thenReturn(SEARCH_QUERY_RESULT);

      final var from = OffsetDateTime.of(2025, 5, 23, 9, 35, 12, 0, ZoneOffset.UTC);
      final var to = OffsetDateTime.of(2025, 12, 18, 17, 22, 33, 0, ZoneOffset.UTC);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder("searchMessageSubscriptions")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "lastUpdatedDate",
                              Map.of(
                                  "from", "2025-05-23T09:35:12Z", "to", "2025-12-18T17:22:33Z"))))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(messageSubscriptionServices).search(queryCaptor.capture(), any());
      final MessageSubscriptionQuery capturedQuery = queryCaptor.getValue();

      assertThat(capturedQuery.filter().dateTimeOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(
              tuple(Operator.GREATER_THAN_EQUALS, from), tuple(Operator.LOWER_THAN, to));
    }

    @Test
    void shouldFailSearchMessageSubscriptionsOnException() {
      // given
      when(messageSubscriptionServices.search(any(MessageSubscriptionQuery.class), any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder("searchMessageSubscriptions").arguments(Map.of()).build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("NOT_FOUND");

      assertTextContentFallback(result);
    }
  }
}
