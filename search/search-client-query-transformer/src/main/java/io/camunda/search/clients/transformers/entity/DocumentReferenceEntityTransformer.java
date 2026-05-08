/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.DocumentReferenceEntity;

public class DocumentReferenceEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.DocumentReferenceEntity, DocumentReferenceEntity> {

  @Override
  public DocumentReferenceEntity apply(
      final io.camunda.webapps.schema.entities.DocumentReferenceEntity source) {
    return new DocumentReferenceEntity(
        source.getVariableKey(),
        source.getVariableName(),
        source.getScopeKey(),
        source.getProcessInstanceKey(),
        source.getProcessDefinitionKey(),
        source.getProcessDefinitionId(),
        source.getRootProcessInstanceKey(),
        source.getTenantId(),
        source.getDocumentId(),
        source.getStoreId(),
        source.getFileName(),
        source.getContentType(),
        source.getSize(),
        source.getExpiresAt(),
        source.getContentHash(),
        source.getCustomProperties());
  }
}
