/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.utils;

import io.camunda.search.entities.ContentItem;
import io.camunda.search.entities.ContentItem.ContentType;
import io.camunda.search.entities.DocumentMetadata;
import io.camunda.search.entities.DocumentReference;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.DocumentReferenceValue;
import java.time.Instant;
import java.util.List;

/**
 * Maps the {@code AgentHistoryMessageContentValue} content-block protocol type to the {@link
 * ContentItem} search-domain type. Shared between {@code AgentHistoryExportHandler} and {@code
 * AgentInstanceExportHandler}, since both export a content-block list (history item content and,
 * respectively, history item / agent instance system prompt) built from the same protocol shape.
 */
public final class AgentContentMapper {

  private AgentContentMapper() {}

  public static List<ContentItem> mapContent(
      final List<? extends AgentHistoryMessageContentValue> content) {
    return content.stream()
        .map(
            c ->
                switch (c.getContentType()) {
                  case TEXT ->
                      new ContentItem(
                          ContentType.TEXT, ExportUtil.emptyToNull(c.getText()), null, null);
                  case DOCUMENT ->
                      new ContentItem(
                          ContentType.DOCUMENT,
                          null,
                          mapDocumentReference(c.getDocumentReference()),
                          null);
                  case OBJECT -> new ContentItem(ContentType.OBJECT, null, null, c.getObject());
                  case UNSPECIFIED ->
                      throw new IllegalStateException(
                          "should never happen — protocol UNSPECIFIED is always overwritten before export");
                })
        .toList();
  }

  private static DocumentReference mapDocumentReference(final DocumentReferenceValue ref) {
    if (ref == null) {
      return null;
    }

    final var meta = ref.getMetadata();
    final var expiresAt =
        meta.getExpiresAt() > 0
            ? DateUtil.toOffsetDateTime(Instant.ofEpochMilli(meta.getExpiresAt()))
            : null;
    final Long processInstanceKey =
        meta.getProcessInstanceKey() > 0 ? meta.getProcessInstanceKey() : null;
    return new DocumentReference(
        ref.getStoreId(),
        ref.getDocumentId(),
        ExportUtil.emptyToNull(ref.getContentHash()),
        new DocumentMetadata(
            meta.getContentType(),
            meta.getFileName(),
            expiresAt,
            meta.getSize(),
            ExportUtil.emptyToNull(meta.getProcessDefinitionId()),
            processInstanceKey,
            meta.getCustomProperties()));
  }
}
