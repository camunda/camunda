/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.webapps.schema.entities.agenthistory.AgentHistoryContentValue;
import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;
import io.camunda.webapps.schema.entities.document.DocumentReferenceMetadataEntity;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue.AgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.DocumentReferenceValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.List;

/**
 * Maps the {@code AgentHistoryMessageContentValue} content-block protocol type to the {@link
 * AgentHistoryContentValue} webapps-schema type. Shared between {@code AgentHistoryHandler} and
 * {@code AgentInstanceHandler}, since both export a content-block list (history item content and,
 * respectively, history item / agent instance system prompt) built from the same protocol shape.
 */
public final class AgentContentMapper {

  private AgentContentMapper() {}

  public static List<AgentHistoryContentValue> mapContent(
      final List<? extends AgentHistoryMessageContentValue> content) {
    return content.stream()
        .map(
            c ->
                switch (c.getContentType()) {
                  case TEXT -> AgentHistoryContentValue.text(ExporterUtil.emptyToNull(c.getText()));
                  case DOCUMENT ->
                      AgentHistoryContentValue.document(
                          mapDocumentReference(c.getDocumentReference()));
                  case OBJECT -> AgentHistoryContentValue.object(c.getObject());
                  // should never happen — protocol UNSPECIFIED is always overwritten before export
                  case UNSPECIFIED ->
                      throw new IllegalStateException(
                          "Unexpected UNSPECIFIED AgentHistoryContentType on an exported record");
                })
        .toList();
  }

  private static DocumentReferenceEntity mapDocumentReference(final DocumentReferenceValue ref) {
    if (ref == null) {
      return null;
    }

    final var meta = ref.getMetadata();
    final var expiresAt =
        meta.getExpiresAt() > 0
            ? DateUtil.toOffsetDateTime(Instant.ofEpochMilli(meta.getExpiresAt()))
            : null;
    return new DocumentReferenceEntity(
        ref.getDocumentId(),
        ref.getStoreId(),
        ref.getContentHash(),
        new DocumentReferenceMetadataEntity(
            meta.getContentType(),
            meta.getFileName(),
            expiresAt,
            meta.getSize(),
            ExporterUtil.emptyToNull(meta.getProcessDefinitionId()),
            ExporterUtil.positiveOrNull(meta.getProcessInstanceKey()),
            meta.getCustomProperties()));
  }
}
