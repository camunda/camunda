/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.agenthistory;

import io.camunda.webapps.schema.entities.document.DocumentReferenceEntity;

/**
 * A single content block in a message. {@code text}, {@code documentReference}, and {@code object}
 * are mutually exclusive based on {@code contentType}. Shared between {@link AgentHistoryEntity}
 * (content/systemPrompt) and {@code AgentInstanceEntity} (systemPrompt).
 */
public record AgentHistoryContentValue(
    AgentHistoryContentType contentType,
    String text,
    DocumentReferenceEntity documentReference,
    Object object) {

  public static AgentHistoryContentValue text(final String text) {
    return new AgentHistoryContentValue(AgentHistoryContentType.TEXT, text, null, null);
  }

  public static AgentHistoryContentValue document(final DocumentReferenceEntity documentReference) {
    return new AgentHistoryContentValue(
        AgentHistoryContentType.DOCUMENT, null, documentReference, null);
  }

  public static AgentHistoryContentValue object(final Object object) {
    return new AgentHistoryContentValue(AgentHistoryContentType.OBJECT, null, null, object);
  }
}
