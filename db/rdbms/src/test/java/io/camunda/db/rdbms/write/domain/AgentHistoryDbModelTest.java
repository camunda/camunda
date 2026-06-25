/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem.ContentType;
import io.camunda.search.entities.AgentInstanceHistoryEntity.DocumentMetadata;
import io.camunda.search.entities.AgentInstanceHistoryEntity.DocumentReference;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
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
    final var model = new AgentHistoryDbModel();
    model.content("[{\"contentType\":\"TEXT\",\"text\":\"hello\",\"documentReference\":null}]");

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
    final var model = new AgentHistoryDbModel();
    model.toolCalls("[{\"toolCallId\":\"call-1\",\"toolName\":\"myTool\",\"elementId\":\"el-1\"}]");

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
  void shouldInvalidateCacheOnMyBatisSetter() {
    // given — model with both forms populated (cache primed)
    final var item = new ContentItem(ContentType.TEXT, "original", null, null);
    final var model = new AgentHistoryDbModel.Builder().contentItems(List.of(item)).build();
    assertThat(model.contentItems().getFirst().text()).isEqualTo("original");

    // when — the JSON form is replaced (simulates the model being re-hydrated from the DB)
    model.content("[{\"contentType\":\"TEXT\",\"text\":\"replaced\",\"documentReference\":null}]");

    // then — the cached list is invalidated and the next read re-derives from the new JSON
    assertThat(model.contentItems()).hasSize(1);
    assertThat(model.contentItems().getFirst().text()).isEqualTo("replaced");
  }
}
