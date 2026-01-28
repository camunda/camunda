/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.element.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mcp.tool.ToolsTest;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ContextConfiguration(classes = {ElementInstanceTools.class})
class ElementInstanceToolsTest extends ToolsTest {

  @MockitoBean private ElementInstanceServices elementInstanceServices;

  @Captor private ArgumentCaptor<SetVariablesRequest> requestCaptor;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(elementInstanceServices);
  }

  @Nested
  class SetProcessInstanceVariables {

    @Test
    void shouldSetVariablesForSingleProcessInstance() {
      // given
      final long processInstanceKey = 100L;
      final Map<String, Object> variables = Map.of("status", "approved", "priority", 1);
      when(elementInstanceServices.setVariables(any()))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("setProcessInstanceVariables")
                  .arguments(
                      Map.of(
                          "processInstanceKeys", processInstanceKey,
                          "variables", variables))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .contains("Updated variables in 1 process instances successfully"));

      verify(elementInstanceServices, times(1)).setVariables(requestCaptor.capture());
      final var capturedRequest = requestCaptor.getValue();
      assertThat(capturedRequest.elementInstanceKey()).isEqualTo(processInstanceKey);
      assertThat(capturedRequest.variables()).isEqualTo(variables);
      assertThat(capturedRequest.local()).isTrue();
    }

    @Test
    void shouldSetVariablesForMultipleProcessInstances() {
      // given
      final List<Long> processInstanceKeys = List.of(100L, 200L, 300L);
      final Map<String, Object> variables = Map.of("status", "approved");
      when(elementInstanceServices.setVariables(any()))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("setProcessInstanceVariables")
                  .arguments(
                      Map.of(
                          "processInstanceKeys", processInstanceKeys,
                          "variables", variables))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .contains("Updated variables in 3 process instances successfully"));

      verify(elementInstanceServices, times(3)).setVariables(requestCaptor.capture());
    }

    @Test
    void shouldHandlePartialFailures() {
      // given
      final List<Long> processInstanceKeys = List.of(100L, 200L, 300L);
      final Map<String, Object> variables = Map.of("status", "updated");
      when(elementInstanceServices.setVariables(any()))
          .thenReturn(CompletableFuture.completedFuture(null))
          .thenReturn(
              CompletableFuture.failedFuture(new ServiceException("Not found", Status.NOT_FOUND)))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("setProcessInstanceVariables")
                  .arguments(
                      Map.of(
                          "processInstanceKeys", processInstanceKeys,
                          "variables", variables))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .contains("Updated variables in 2 process instances successfully")
                      .contains("1 failed"));
    }
  }

  @Nested
  class SetElementInstanceVariables {

    @Test
    void shouldSetVariablesForSingleElementInstance() {
      // given
      final long elementInstanceKey = 456L;
      final Map<String, Object> variables = Map.of("loopCounter", 0);
      when(elementInstanceServices.setVariables(any()))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("setElementInstanceVariables")
                  .arguments(
                      Map.of(
                          "elementInstanceKeys", elementInstanceKey,
                          "variables", variables))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .contains("Updated variables in 1 element instances successfully"));

      verify(elementInstanceServices, times(1)).setVariables(requestCaptor.capture());
      final var capturedRequest = requestCaptor.getValue();
      assertThat(capturedRequest.elementInstanceKey()).isEqualTo(elementInstanceKey);
      assertThat(capturedRequest.variables()).isEqualTo(variables);
      assertThat(capturedRequest.local()).isTrue();
    }

    @Test
    void shouldSetVariablesForMultipleElementInstances() {
      // given
      final List<Long> elementInstanceKeys = List.of(100L, 200L);
      final Map<String, Object> variables = Map.of("data", "value");
      when(elementInstanceServices.setVariables(any()))
          .thenReturn(CompletableFuture.completedFuture(null));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("setElementInstanceVariables")
                  .arguments(
                      Map.of(
                          "elementInstanceKeys", elementInstanceKeys,
                          "variables", variables))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .contains("Updated variables in 2 element instances successfully"));

      verify(elementInstanceServices, times(2)).setVariables(requestCaptor.capture());
    }
  }
}
