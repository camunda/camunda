/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.templates;

import io.camunda.tasklist.schema.v86.backup.Prio2Backup;
import io.camunda.tasklist.schema.v86.indices.ProcessInstanceDependant;
import org.springframework.stereotype.Component;

@Component
public class TaskTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio2Backup {

  public static final String INDEX_NAME = "task";
  public static final String INDEX_VERSION = "8.5.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String CREATION_TIME = "creationTime";
  public static final String COMPLETION_TIME = "completionTime";
  public static final String FLOW_NODE_BPMN_ID = "flowNodeBpmnId";
  public static final String STATE = "state";
  public static final String ASSIGNEE = "assignee";
  public static final String CANDIDATE_GROUPS = "candidateGroups";
  public static final String CANDIDATE_USERS = "candidateUsers";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String DUE_DATE = "dueDate";
  public static final String FORM_ID = "formId";
  public static final String FORM_KEY = "formKey";
  public static final String FOLLOW_UP_DATE = "followUpDate";
  public static final String TENANT_ID = "tenantId";
  public static final String IMPLEMENTATION = "implementation";
  public static final String PRIORITY = "priority";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getAllIndicesPattern() {
    return super.getIndexPattern();
  }
}
