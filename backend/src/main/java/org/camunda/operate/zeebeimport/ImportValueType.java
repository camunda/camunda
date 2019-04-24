/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import org.camunda.operate.zeebeimport.record.value.DeploymentRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.IncidentRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.JobRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.VariableRecordValueImpl;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.protocol.clientapi.ValueType;

public class ImportValueType {


  public final static ImportValueType WORKFLOW_INSTANCE = new ImportValueType(ValueType.WORKFLOW_INSTANCE, ZeebeESConstants.WORKFLOW_INSTANCE_INDEX_NAME, WorkflowInstanceRecordValueImpl.class);
  public final static ImportValueType JOB = new ImportValueType(ValueType.JOB, ZeebeESConstants.JOB_INDEX_NAME, JobRecordValueImpl.class);
  public final static ImportValueType INCIDENT = new ImportValueType(ValueType.INCIDENT, ZeebeESConstants.INCIDENT_INDEX_NAME, IncidentRecordValueImpl.class);
  public final static ImportValueType DEPLOYMENT = new ImportValueType(ValueType.DEPLOYMENT, ZeebeESConstants.DEPLOYMENT_INDEX_NAME, DeploymentRecordValueImpl.class);
  public final static ImportValueType VARIABLE = new ImportValueType(ValueType.VARIABLE, ZeebeESConstants.VARIABLE_INDEX_NAME, VariableRecordValueImpl.class);

  public static final ImportValueType[] IMPORT_VALUE_TYPES = new ImportValueType[]{
      ImportValueType.DEPLOYMENT,
      ImportValueType.WORKFLOW_INSTANCE,
      ImportValueType.JOB,
      ImportValueType.INCIDENT,
      ImportValueType.VARIABLE};

  private final ValueType valueType;
  private final String aliasTemplate;
  private final Class<? extends RecordValue> recordValueClass;

  public ImportValueType(ValueType valueType, String aliasTemplate, Class<? extends RecordValue> recordValueClass) {
    this.valueType = valueType;
    this.aliasTemplate = aliasTemplate;
    this.recordValueClass = recordValueClass;
  }

  public ValueType getValueType() {
    return valueType;
  }

  public String getAliasTemplate() {
    return aliasTemplate;
  }

  public Class<? extends RecordValue> getRecordValueClass() {
    return recordValueClass;
  }

  public String getAliasName(String prefix) {
    return String.format("%s-%s", prefix, aliasTemplate);
  }

  @Override public String toString() {
    return valueType.toString();
  }
}
