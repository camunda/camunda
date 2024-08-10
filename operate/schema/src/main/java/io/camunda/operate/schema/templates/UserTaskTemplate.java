/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.templates;

import io.camunda.operate.schema.backup.Prio3Backup;
import org.springframework.stereotype.Component;

@Component
public class UserTaskTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "user-task";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String USER_TASK_KEY = "userTaskKey";
  public static final String ASSIGNEE = "assignee";
  public static final String CANDIDATE_GROUPS = "candidateGroups";
  public static final String CANDIDATE_USERS = "candidateUsers";
  public static final String DUE_DATE = "dueDate";
  public static final String FOLLOW_UP_DATE = "followUpDate";
  public static final String FORM_KEY = "formKey";
  public static final String ELEMENT_ID = "elementId";
  public static final String ELEMENT_INSTANCE_KEY = "elementInstanceKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String VARIABLES = "variables";
  public static final String EXTERNAL_REFERENCE = "externalReference";
  public static final String ACTION = "action";
  public static final String CHANGED_ATTRIBUTES = "changedAttributes";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.5.0";
  }
}
