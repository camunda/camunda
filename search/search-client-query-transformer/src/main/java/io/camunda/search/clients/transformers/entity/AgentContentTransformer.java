/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.entities.ContentItem;
import io.camunda.search.entities.ContentItem.ContentType;
import io.camunda.search.entities.DocumentMetadata;
import io.camunda.search.entities.DocumentReference;
import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;
import java.util.List;

/**
 * Maps the webapps-schema {@link AgentHistoryContentValue} content-block type to the search-domain
 * {@link ContentItem} type. Shared between {@code AgentHistoryEntityTransformer} and {@code
 * AgentInstanceEntityTransformer}, since both convert a content-block list (history item content
 * and, respectively, history item / agent instance system prompt) built from the same
 * webapps-schema shape.
 */
final class AgentContentTransformer {

  private AgentContentTransformer() {}

  static List<ContentItem> toContent(final List<AgentHistoryContentValue> content) {
    if (content == null) {
      return List.of();
    }
    return content.stream().map(AgentContentTransformer::toContentItem).toList();
  }

  private static ContentItem toContentItem(final AgentHistoryContentValue value) {
    return switch (value.contentType()) {
      case TEXT -> new ContentItem(ContentType.TEXT, value.text(), null, null);
      case DOCUMENT ->
          new ContentItem(
              ContentType.DOCUMENT, null, toDocumentReference(value.documentReference()), null);
      case OBJECT -> new ContentItem(ContentType.OBJECT, null, null, value.object());
    };
  }

  private static DocumentReference toDocumentReference(final DocumentReferenceEntity entity) {
    if (entity == null) {
      return null;
    }
    final var meta = entity.metadata();
    return new DocumentReference(
        entity.storeId(),
        entity.documentId(),
        entity.contentHash(),
        new DocumentMetadata(
            meta.contentType(),
            meta.fileName(),
            meta.expiresAt(),
            meta.size(),
            meta.processDefinitionId(),
            meta.processInstanceKey(),
            meta.customProperties()));
  }
}
