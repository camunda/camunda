/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.document;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Metadata for a {@link DocumentReferenceEntity}. {@code expiresAt} and {@code processInstanceKey}
 * are nullable — protocol sentinel {@code -1} is converted to {@code null} during mapping.
 */
public record DocumentReferenceMetadataEntity(
    String contentType,
    String fileName,
    OffsetDateTime expiresAt,
    long size,
    String processDefinitionId,
    Long processInstanceKey,
    Map<String, Object> customProperties) {}
