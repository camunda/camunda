/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem.ContentType;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryCommitStatus;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryEntity.AgentHistoryEmbeddedToolCallValue;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryRole;
import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;
import io.camunda.webapps.schema.entities.document.DocumentReferenceMetadataEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AgentHistoryEntityTransformerTest {

  private final AgentHistoryEntityTransformer transformer = new AgentHistoryEntityTransformer();

  private AgentHistoryEntity buildSource() {
    final var source = new AgentHistoryEntity();
    source.setId("1");
    source.setKey(100L);
    source.setAgentInstanceKey(50L);
    source.setElementInstanceKey(200L);
    source.setProcessInstanceKey(300L);
    source.setRootProcessInstanceKey(250L);
    source.setBpmnProcessId("my-process");
    source.setProcessDefinitionKey(400L);
    source.setTenantId("<default>");
    source.setPartitionId(1);
    source.setJobKey(500L);
    source.setJobLease("lease-token");
    source.setIteration(3);
    source.setRole(AgentHistoryRole.ASSISTANT);
    source.setCommitStatus(AgentHistoryCommitStatus.COMMITTED);
    source.setProducedAt(OffsetDateTime.parse("2024-06-01T12:00:00Z"));
    source.setInputTokens(50L);
    source.setOutputTokens(30L);
    source.setDurationMs(1200L);
    source.setContent(List.of(AgentHistoryContentValue.text("Hello, world!")));
    source.setToolCalls(
        List.of(
            new AgentHistoryEmbeddedToolCallValue(
                "tc-1", "search", "elem-1", Map.of("query", "weather"))));
    return source;
  }

  @Test
  void shouldMapAllFields() {
    // given
    final var source = buildSource();

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then
    assertThat(result.historyItemKey()).isEqualTo(100L);
    assertThat(result.agentInstanceKey()).isEqualTo(50L);
    assertThat(result.elementInstanceKey()).isEqualTo(200L);
    assertThat(result.processInstanceKey()).isEqualTo(300L);
    assertThat(result.processDefinitionKey()).isEqualTo(400L);
    assertThat(result.processDefinitionId()).isEqualTo("my-process");
    assertThat(result.tenantId()).isEqualTo("<default>");
    assertThat(result.jobKey()).isEqualTo(500L);
    assertThat(result.jobLease()).isEqualTo("lease-token");
    assertThat(result.iteration()).isEqualTo(3);
    assertThat(result.role()).isEqualTo(AgentInstanceHistoryRole.ASSISTANT);
    assertThat(result.commitStatus()).isEqualTo(AgentInstanceHistoryCommitStatus.COMMITTED);
    assertThat(result.producedAt()).isEqualTo(OffsetDateTime.parse("2024-06-01T12:00:00Z"));
    assertThat(result.metrics().inputTokens()).isEqualTo(50L);
    assertThat(result.metrics().outputTokens()).isEqualTo(30L);
    assertThat(result.metrics().durationMs()).isEqualTo(1200L);
    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).contentType()).isEqualTo(ContentType.TEXT);
    assertThat(result.content().get(0).text()).isEqualTo("Hello, world!");
    assertThat(result.toolCalls()).hasSize(1);
    assertThat(result.toolCalls().get(0).toolCallId()).isEqualTo("tc-1");
    assertThat(result.toolCalls().get(0).toolName()).isEqualTo("search");
    assertThat(result.toolCalls().get(0).elementId()).isEqualTo("elem-1");
    assertThat(result.toolCalls().get(0).arguments()).isEqualTo(Map.of("query", "weather"));
  }

  @Test
  void shouldMapNullContentToEmptyList() {
    // given
    final var source = buildSource();
    source.setContent(null);

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then
    assertThat(result.content()).isEmpty();
  }

  @Test
  void shouldMapNullToolCallsToEmptyList() {
    // given
    final var source = buildSource();
    source.setToolCalls(null);

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then
    assertThat(result.toolCalls()).isEmpty();
  }

  @Test
  void shouldMapNullIterationAsNull() {
    // given
    final var source = buildSource();
    source.setIteration(null);

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then
    assertThat(result.iteration()).isNull();
  }

  @Test
  void shouldMapTextContent() {
    // given
    final var source = buildSource();
    source.setContent(List.of(AgentHistoryContentValue.text("some text")));

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then
    assertThat(result.content()).hasSize(1);
    final var item = result.content().get(0);
    assertThat(item.contentType()).isEqualTo(ContentType.TEXT);
    assertThat(item.text()).isEqualTo("some text");
    assertThat(item.documentReference()).isNull();
    assertThat(item.object()).isEmpty();
  }

  @Test
  void shouldMapDocumentContent() {
    // given
    final var metadata =
        new DocumentReferenceMetadataEntity(
            "application/pdf", "report.pdf", null, 2048L, null, null, Map.of());
    final var docRefEntity = new DocumentReferenceEntity("doc-1", "store-1", "hash123", metadata);
    final var source = buildSource();
    source.setContent(List.of(AgentHistoryContentValue.document(docRefEntity)));

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then
    assertThat(result.content()).hasSize(1);
    final var item = result.content().get(0);
    assertThat(item.contentType()).isEqualTo(ContentType.DOCUMENT);
    assertThat(item.text()).isNull();
    final var docRef = item.documentReference();
    assertThat(docRef).isNotNull();
    assertThat(docRef.storeId()).isEqualTo("store-1");
    assertThat(docRef.documentId()).isEqualTo("doc-1");
    assertThat(docRef.contentHash()).isEqualTo("hash123");
    assertThat(docRef.metadata().contentType()).isEqualTo("application/pdf");
    assertThat(docRef.metadata().fileName()).isEqualTo("report.pdf");
    assertThat(docRef.metadata().size()).isEqualTo(2048L);
  }

  @Test
  void shouldMapObjectContent() {
    // given
    final var objectData = Map.<String, Object>of("result", "42");
    final var source = buildSource();
    source.setContent(List.of(AgentHistoryContentValue.object(objectData)));

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then
    assertThat(result.content()).hasSize(1);
    final var item = result.content().get(0);
    assertThat(item.contentType()).isEqualTo(ContentType.OBJECT);
    assertThat(item.object()).isEqualTo(objectData);
  }

  @ParameterizedTest
  @EnumSource(AgentHistoryRole.class)
  void shouldMapEveryRole(final AgentHistoryRole schemaRole) {
    // given
    final var source = buildSource();
    source.setRole(schemaRole);

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then — 1:1 name mapping
    assertThat(result.role()).isNotNull();
    assertThat(result.role().name()).isEqualTo(schemaRole.name());
  }

  @ParameterizedTest
  @EnumSource(AgentHistoryCommitStatus.class)
  void shouldMapEveryCommitStatus(final AgentHistoryCommitStatus schemaStatus) {
    // given
    final var source = buildSource();
    source.setCommitStatus(schemaStatus);

    // when
    final AgentInstanceHistoryEntity result = transformer.apply(source);

    // then — 1:1 name mapping
    assertThat(result.commitStatus()).isNotNull();
    assertThat(result.commitStatus().name()).isEqualTo(schemaStatus.name());
  }
}
