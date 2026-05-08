/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.index;

import static io.camunda.webapps.schema.descriptors.ComponentNames.CAMUNDA;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import java.util.Optional;

public class DocumentReferenceIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "document-reference";
  public static final String INDEX_VERSION = "8.9.0";

  public static final String ID = "id";
  public static final String VARIABLE_KEY = "variableKey";
  public static final String VARIABLE_NAME = "variableName";
  public static final String SCOPE_KEY = "scopeKey";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "processDefinitionId";
  public static final String ROOT_PROCESS_INSTANCE_KEY = "rootProcessInstanceKey";
  public static final String TENANT_ID = "tenantId";
  public static final String POSITION = "position";
  public static final String PARTITION_ID = "partitionId";
  public static final String DOCUMENT_ID = "documentId";
  public static final String STORE_ID = "storeId";
  public static final String FILE_NAME = "fileName";
  public static final String CONTENT_TYPE = "contentType";
  public static final String SIZE = "size";
  public static final String EXPIRES_AT = "expiresAt";
  public static final String CONTENT_HASH = "contentHash";
  public static final String CUSTOM_PROPERTIES = "customProperties";

  public DocumentReferenceIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public Optional<String> getTenantIdField() {
    return Optional.of(TENANT_ID);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return CAMUNDA.toString();
  }
}
