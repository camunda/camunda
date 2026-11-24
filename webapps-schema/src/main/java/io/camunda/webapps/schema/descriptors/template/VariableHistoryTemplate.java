/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import java.util.Optional;

/**
 * Template descriptor for the {@code variable-history} index, which holds one document per variable
 * change. It is {@link ProcessInstanceDependant} so the archiver and the history deletion handle
 * it, and {@link Prio4Backup} like the audit log.
 */
public class VariableHistoryTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio4Backup {

  public static final String INDEX_NAME = "variable-history";
  public static final String INDEX_VERSION = "8.11.0";

  public static final String VARIABLE_HISTORY_ITEM_KEY = "variableHistoryItemKey";
  public static final String VARIABLE_KEY = "variableKey";
  public static final String NAME = "name";
  public static final String VALUE = "value";
  public static final String FULL_VALUE = "fullValue";
  public static final String IS_PREVIEW = "isPreview";
  public static final String SCOPE_KEY = "scopeKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String TIMESTAMP = "timestamp";
  public static final String OPERATION_TYPE = "operationType";
  public static final String VARIABLE_OPERATION_TYPE = "variableOperationType";
  public static final String ELEMENT_INSTANCE_KEY = "elementInstanceKey";
  public static final String ELEMENT_ID = "elementId";
  public static final String ACTOR_TYPE = "actorType";
  public static final String ACTOR_ID = "actorId";
  public static final String AGENT_ELEMENT_ID = "agentElementId";
  public static final String ITEM_INDEX = "itemIndex";

  public VariableHistoryTemplate(final String indexPrefix, final boolean isElasticsearch) {
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
    return ComponentNames.CAMUNDA.toString();
  }
}
