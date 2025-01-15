/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.operate.template;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import java.util.Optional;

public class FlowNodeInstanceTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio4Backup {

  public static final String INDEX_NAME = "flownode-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  @Deprecated public static final String INCIDENT_KEY = "incidentKey";
  public static final String STATE = "state";
  public static final String TYPE = "type";
  public static final String TREE_PATH = "treePath";
  public static final String LEVEL = "level";
  public static final String INCIDENT = "incident"; // true/false
  public static final String SCOPE_KEY = "scopeKey";

  public FlowNodeInstanceTemplate(final String indexPrefix, final boolean isElasticsearch) {
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
    return "8.3.1";
  }

  @Override
  public String getComponentName() {
    return OPERATE.toString();
  }
}
