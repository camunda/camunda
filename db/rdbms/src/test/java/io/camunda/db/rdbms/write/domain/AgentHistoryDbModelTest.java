/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AgentInstanceHistoryEntity.Tool;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
import io.camunda.search.entities.ContentItem;
import io.camunda.search.entities.ContentItem.ContentType;
import io.camunda.search.entities.DocumentMetadata;
import io.camunda.search.entities.DocumentReference;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentHistoryDbModelTest {

  @Test
  void shouldSerializeContentToJson() {
    // given
    final var item = new ContentItem(ContentType.TEXT, "hello", null, null);

    // when
    final var model = new AgentHistoryDbModel.Builder().contentItems(List.of(item)).build();

    // then — serialized CLOB field is populated and contains the content text
    assertThat(model.contentItems()).containsExactly(item);
    assertThat(model.content()).contains("\"text\":\"hello\"");
  }

  @Test
  void shouldDeserializeContentFromJson() {
    // given — simulate a model hydrated from the DB: only the JSON form is populated
    final var model =
        new AgentHistoryDbModel.Builder(
                "[{\"contentType\":\"TEXT\",\"text\":\"hello\",\"documentReference\":null}]",
                null,
                null,
                null)
            .build();

    // when
    final List<ContentItem> deserialized = model.contentItems();

    // then
    assertThat(deserialized).hasSize(1);
    assertThat(deserialized.getFirst().contentType()).isEqualTo(ContentType.TEXT);
    assertThat(deserialized.getFirst().text()).isEqualTo("hello");
  }

  @Test
  void shouldSerializeToolCallsToJson() {
    // given
    final var toolCall = new ToolCall("call-1", "myTool", "el-1", null);

    // when
    final var model = new AgentHistoryDbModel.Builder().toolCallValues(List.of(toolCall)).build();

    // then — serialized CLOB field is populated and contains the tool name
    assertThat(model.toolCallValues()).containsExactly(toolCall);
    assertThat(model.toolCalls()).contains("\"toolName\":\"myTool\"");
  }

  @Test
  void shouldDeserializeToolCallsFromJson() {
    // given — simulate a model hydrated from the DB: only the JSON form is populated
    final var model =
        new AgentHistoryDbModel.Builder(
                null,
                "[{\"toolCallId\":\"call-1\",\"toolName\":\"myTool\",\"elementId\":\"el-1\"}]",
                null,
                null)
            .build();

    // when
    final List<ToolCall> deserialized = model.toolCallValues();

    // then
    assertThat(deserialized).hasSize(1);
    assertThat(deserialized.getFirst().toolCallId()).isEqualTo("call-1");
    assertThat(deserialized.getFirst().toolName()).isEqualTo("myTool");
    assertThat(deserialized.getFirst().elementId()).isEqualTo("el-1");
  }

  @Test
  void shouldReturnNullForNullOrEmptyLists() {
    // given — empty list passed to builder
    final var modelWithEmptyContent =
        new AgentHistoryDbModel.Builder().contentItems(List.of()).build();
    final var modelWithEmptyToolCalls =
        new AgentHistoryDbModel.Builder().toolCallValues(List.of()).build();

    // then — empty list serializes to null JSON (not "[]"), so the CLOB columns are null
    assertThat(modelWithEmptyContent.content()).isNull();
    assertThat(modelWithEmptyToolCalls.toolCalls()).isNull();

    // given — null list passed to builder
    final var modelWithNullContent = new AgentHistoryDbModel.Builder().contentItems(null).build();
    final var modelWithNullToolCalls =
        new AgentHistoryDbModel.Builder().toolCallValues(null).build();

    // then — null list also results in null JSON (not "[]")
    assertThat(modelWithNullContent.content()).isNull();
    assertThat(modelWithNullToolCalls.toolCalls()).isNull();
  }

  @Test
  void shouldSerializeDocumentContentWithExpiresAt() {
    // given — DocumentMetadata with a non-null expiresAt field (OffsetDateTime)
    final var expiresAt = OffsetDateTime.parse("2030-01-01T00:00:00Z");
    final var metadata =
        new DocumentMetadata(
            "application/pdf", "report.pdf", expiresAt, 1024L, null, null, Map.of());
    final var docRef = new DocumentReference("store-1", "doc-1", null, metadata);
    final var item = new ContentItem(ContentType.DOCUMENT, null, docRef, null);

    // when
    final var model = new AgentHistoryDbModel.Builder().contentItems(List.of(item)).build();

    // then — JavaTimeModule must be registered; without it the MAPPER would throw and return null
    assertThat(model.content())
        .as("content must not be null — OffsetDateTime serialization requires JavaTimeModule")
        .isNotNull();
    assertThat(model.content()).contains("expiresAt");
  }

  @Test
  void shouldDeriveContentAndToolCallsFreshOnEveryCall() {
    // given
    final var item = new ContentItem(ContentType.TEXT, "hello", null, null);
    final var toolCall = new ToolCall("call-1", "myTool", "el-1", null);
    final var model =
        new AgentHistoryDbModel.Builder()
            .contentItems(List.of(item))
            .toolCallValues(List.of(toolCall))
            .build();

    // then — equal content, but freshly deserialized each time (no cache)
    assertThat(model.contentItems())
        .isEqualTo(model.contentItems())
        .isNotSameAs(model.contentItems());
    assertThat(model.toolCallValues())
        .isEqualTo(model.toolCallValues())
        .isNotSameAs(model.toolCallValues());
  }

  @Test
  void shouldSerializeToolsToJson() {
    // given — CONFIGURATION-only tool list
    final var tool = new Tool("search", "Searches the web", "Task_1");

    // when
    final var model = new AgentHistoryDbModel.Builder().toolValues(List.of(tool)).build();

    // then — serialized CLOB field is populated and contains the tool name
    assertThat(model.toolValues()).containsExactly(tool);
    assertThat(model.tools()).contains("\"name\":\"search\"");
  }

  @Test
  void shouldDeserializeToolsFromJson() {
    // given — simulate a model hydrated from the DB: only the JSON form is populated
    final var model =
        new AgentHistoryDbModel.Builder(
                null,
                null,
                "[{\"name\":\"search\",\"description\":\"Searches the web\",\"elementId\":\"Task_1\"}]",
                null)
            .build();

    // when
    final List<Tool> deserialized = model.toolValues();

    // then
    assertThat(deserialized).hasSize(1);
    assertThat(deserialized.getFirst().name()).isEqualTo("search");
    assertThat(deserialized.getFirst().description()).isEqualTo("Searches the web");
    assertThat(deserialized.getFirst().elementId()).isEqualTo("Task_1");
  }

  @Test
  void shouldReturnNullForNullOrEmptyToolsList() {
    // given — empty list passed to builder
    final var modelWithEmptyTools = new AgentHistoryDbModel.Builder().toolValues(List.of()).build();

    // then — empty list serializes to null JSON (not "[]"), so the CLOB column is null, but
    // toolValues() still returns an empty list, never null
    assertThat(modelWithEmptyTools.tools()).isNull();
    assertThat(modelWithEmptyTools.toolValues()).isEmpty();

    // given — null list passed to builder
    final var modelWithNullTools = new AgentHistoryDbModel.Builder().toolValues(null).build();

    // then — null list also results in null JSON (not "[]")
    assertThat(modelWithNullTools.tools()).isNull();
    assertThat(modelWithNullTools.toolValues()).isEmpty();
  }

  @Test
  void shouldSetAndGetHistoryItemIdModelProviderAndLimits() {
    // given / when
    final var model =
        new AgentHistoryDbModel.Builder()
            .historyItemId("history-item-1")
            .model("gpt-4o")
            .provider("openai")
            .maxTokens(1000L)
            .maxModelCalls(10)
            .maxToolCalls(5)
            .build();

    // then
    assertThat(model.historyItemId()).isEqualTo("history-item-1");
    assertThat(model.model()).isEqualTo("gpt-4o");
    assertThat(model.provider()).isEqualTo("openai");
    assertThat(model.maxTokens()).isEqualTo(1000L);
    assertThat(model.maxModelCalls()).isEqualTo(10);
    assertThat(model.maxToolCalls()).isEqualTo(5);
  }

  @Test
  void shouldSerializeSystemPromptToJson() {
    // given — CONFIGURATION-only system prompt content
    final var item = new ContentItem(ContentType.TEXT, "You are a helpful assistant.", null, null);

    // when
    final var model = new AgentHistoryDbModel.Builder().systemPromptItems(List.of(item)).build();

    // then — serialized CLOB field is populated and contains the prompt text
    assertThat(model.systemPromptItems()).containsExactly(item);
    assertThat(model.systemPrompt()).contains("\"text\":\"You are a helpful assistant.\"");
  }

  @Test
  void shouldDeserializeSystemPromptFromJson() {
    // given — simulate a model hydrated from the DB: only the JSON form is populated
    final var model =
        new AgentHistoryDbModel.Builder(
                null,
                null,
                null,
                "[{\"contentType\":\"TEXT\",\"text\":\"You are a helpful assistant.\",\"documentReference\":null}]")
            .build();

    // when
    final List<ContentItem> deserialized = model.systemPromptItems();

    // then
    assertThat(deserialized).hasSize(1);
    assertThat(deserialized.getFirst().contentType()).isEqualTo(ContentType.TEXT);
    assertThat(deserialized.getFirst().text()).isEqualTo("You are a helpful assistant.");
  }

  @Test
  void shouldReturnNullForNullOrEmptySystemPrompt() {
    // given — empty list passed to builder
    final var modelWithEmptySystemPrompt =
        new AgentHistoryDbModel.Builder().systemPromptItems(List.of()).build();

    // then — empty list serializes to null JSON (not "[]"), so the CLOB column is null, but
    // systemPromptItems() still returns an empty list, never null
    assertThat(modelWithEmptySystemPrompt.systemPrompt()).isNull();
    assertThat(modelWithEmptySystemPrompt.systemPromptItems()).isEmpty();

    // given — null list passed to builder
    final var modelWithNullSystemPrompt =
        new AgentHistoryDbModel.Builder().systemPromptItems(null).build();

    // then — null list also results in null JSON (not "[]")
    assertThat(modelWithNullSystemPrompt.systemPrompt()).isNull();
    assertThat(modelWithNullSystemPrompt.systemPromptItems()).isEmpty();
  }

  @Test
  void shouldDeriveSystemPromptFreshOnEveryCall() {
    // given
    final var item = new ContentItem(ContentType.TEXT, "hello", null, null);
    final var model = new AgentHistoryDbModel.Builder().systemPromptItems(List.of(item)).build();

    // then — equal content, but freshly deserialized each time (no cache)
    assertThat(model.systemPromptItems())
        .isEqualTo(model.systemPromptItems())
        .isNotSameAs(model.systemPromptItems());
  }
}
