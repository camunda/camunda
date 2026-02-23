/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.validation.annotation.Validated;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {McpToolParamsUnwrappedValidationTest.TestTools.class})
class McpToolParamsUnwrappedValidationTest extends ToolsTest {

  @MockitoBean private TestTaskService taskService;
  @Captor private ArgumentCaptor<TaskRequest> taskRequestCaptor;

  public record TaskRequest(
      @McpToolParam(description = "Task name") @NotBlank String name,
      @McpToolParam(description = "Task priority") @Positive Integer priority,
      @McpToolParam(description = "Task tags", required = false) Set<@NotBlank String> tags) {}

  @Component
  @Validated
  static class TestTools {

    private final TestTaskService taskService;

    TestTools(final TestTaskService taskService) {
      this.taskService = taskService;
    }

    @CamundaMcpTool(description = "Create a task")
    public CallToolResult createTask(@McpToolParamsUnwrapped @Valid final TaskRequest request) {
      taskService.process(request);
      return CallToolResultMapper.from("created");
    }

    @CamundaMcpTool(description = "Get a task by key")
    public CallToolResult getTask(
        @McpToolParam(description = "Task key") @Positive final Long taskKey) {
      return CallToolResultMapper.from("task-" + taskKey);
    }
  }

  // --- Test models and tools ---

  public interface TestTaskService {
    void process(TaskRequest request);
  }

  @Nested
  class WrapperParamValidation {

    @Test
    void shouldDeserializeAndPassWrappedDto() {
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(
                      Map.of("name", "My Task", "priority", 5, "tags", Set.of("urgent", "review")))
                  .build());

      assertThat(result.isError()).isFalse();

      verify(taskService).process(taskRequestCaptor.capture());
      final TaskRequest captured = taskRequestCaptor.getValue();
      assertThat(captured.name()).isEqualTo("My Task");
      assertThat(captured.priority()).isEqualTo(5);
      assertThat(captured.tags()).containsExactlyInAnyOrder("urgent", "review");
    }

    @Test
    void shouldReturnValidationErrorWithNormalizedPath() {
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(Map.of("name", "", "priority", 1))
                  .build());

      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              // path is "name", not "createTask.request.name"
              textContent -> assertThat(textContent.text()).isEqualTo("name: must not be blank"));
    }

    @Test
    void shouldReturnMultipleValidationErrors() {
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(Map.of("name", "", "priority", -1))
                  .build());

      assertThat(result.isError()).isTrue();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent -> {
                assertThat(textContent.text()).contains("name: must not be blank");
                assertThat(textContent.text()).contains("priority: must be greater than 0");
              });
    }

    @Test
    void shouldValidateCollectionElements() {
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createTask")
                  .arguments(Map.of("name", "Valid", "priority", 1, "tags", Set.of("")))
                  .build());

      assertThat(result.isError()).isTrue();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent -> assertThat(textContent.text()).contains("must not be blank"));
    }
  }

  @Nested
  class IndividualParamValidation {

    @Test
    void shouldReturnErrorOnIndividualParamValidation() {
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("getTask").arguments(Map.of("taskKey", -1L)).build());

      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              // path is "taskKey", not "getTask.taskKey"
              textContent ->
                  assertThat(textContent.text()).isEqualTo("taskKey: must be greater than 0"));
    }
  }
}
