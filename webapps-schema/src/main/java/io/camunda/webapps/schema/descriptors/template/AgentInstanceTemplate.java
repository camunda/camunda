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
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import java.util.Optional;

public class AgentInstanceTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "agent-instance";
  public static final String INDEX_VERSION = "8.10.0";

  public static final String KEY = "key";
  public static final String ELEMENT_ID = "elementId";
  public static final String ELEMENT_INSTANCE_KEYS = "elementInstanceKeys";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String ROOT_PROCESS_INSTANCE_KEY = "rootProcessInstanceKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";
  public static final String VERSION_TAG = "versionTag";
  public static final String TENANT_ID = "tenantId";
  public static final String STATUS = "status";
  public static final String MODEL = "model";
  public static final String PROVIDER = "provider";
  public static final String SYSTEM_PROMPT = "systemPrompt";
  public static final String MAX_TOKENS = "maxTokens";
  public static final String MAX_MODEL_CALLS = "maxModelCalls";
  public static final String MAX_TOOL_CALLS = "maxToolCalls";
  public static final String INPUT_TOKENS = "inputTokens";
  public static final String OUTPUT_TOKENS = "outputTokens";
  public static final String MODEL_CALLS = "modelCalls";
  public static final String TOOL_CALLS = "toolCalls";
  public static final String TOOLS = "tools";
  public static final String CREATION_DATE = "creationDate";
  public static final String LAST_UPDATED_DATE = "lastUpdatedDate";
  public static final String COMPLETION_DATE = "completionDate";

  public AgentInstanceTemplate(final String indexPrefix, final boolean isElasticsearch) {
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
