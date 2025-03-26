/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;

public class OperationTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "operation";

  public static final String ID = "id";
  public static final String TYPE = "type";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String DECISION_DEFINITION_KEY = "decisionDefinitionKey";
  public static final String INCIDENT_KEY = "incidentKey";
  public static final String SCOPE_KEY = "scopeKey";
  public static final String VARIABLE_NAME = "variableName";
  public static final String VARIABLE_VALUE = "variableValue";
  public static final String STATE = "state";
  public static final String ERROR_MSG = "errorMessage";
  public static final String LOCK_EXPIRATION_TIME = "lockExpirationTime";
  public static final String LOCK_OWNER = "lockOwner";
  public static final String BATCH_OPERATION_ID = "batchOperationId";
  public static final String ZEEBE_COMMAND_KEY = "zeebeCommandKey";
  public static final String USERNAME = "username";
  public static final String MODIFIY_INSTRUCTIONS = "modifyInstructions";
  public static final String MIGRATION_PLAN = "migrationPlan";
  public static final String METADATA_AGGREGATION = "metadataAggregation";
  public static final String BATCH_OPERATION_ID_AGGREGATION = "batchOperationIdAggregation";
  public static final String COMPLETED_DATE = "completedDate";

  public OperationTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.4.1";
  }

  @Override
  public String getComponentName() {
    return OPERATE.toString();
  }
}
