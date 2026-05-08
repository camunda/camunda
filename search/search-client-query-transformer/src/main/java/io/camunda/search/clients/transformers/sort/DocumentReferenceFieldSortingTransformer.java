/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.DOCUMENT_ID;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.FILE_NAME;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.index.DocumentReferenceIndex.VARIABLE_KEY;

public class DocumentReferenceFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "scopeKey" -> SCOPE_KEY;
      case "variableKey" -> VARIABLE_KEY;
      case "documentId" -> DOCUMENT_ID;
      case "fileName" -> FILE_NAME;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return VARIABLE_KEY;
  }
}
