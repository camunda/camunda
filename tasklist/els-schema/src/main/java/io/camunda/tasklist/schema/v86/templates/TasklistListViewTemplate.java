/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.templates;

import static java.util.Optional.empty;
import static java.util.Optional.of;

import io.camunda.tasklist.schema.v86.backup.Prio3Backup;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TasklistListViewTemplate extends AbstractTemplateDescriptor implements Prio3Backup {

  public static final String INDEX_NAME = "list-view";
  public static final String INDEX_VERSION = "8.6.0";

  public static final String ID = "id";
  public static final String USER_TASK_KEY = "key";
  public static final String TASK_ID = "taskId";
  public static final String VARIABLE_NAME = "name";
  public static final String VARIABLE_VALUE = "value";
  public static final String VARIABLE_FULL_VALUE = "fullValue";
  public static final String IS_PREVIEW = "isPreview";
  public static final String TENANT_ID = "tenantId";
  public static final String FLOW_NODE_BPMN_ID = "flowNodeBpmnId";
  public static final String FLOW_NODE_INSTANCE_ID = "flowNodeInstanceId";
  public static final String PARTITION_ID = "partitionId";
  public static final String COMPLETION_TIME = "completionTime";
  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String POSITION = "position";
  public static final String STATE = "state";
  public static final String CREATION_TIME = "creationTime";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String ASSIGNEE = "assignee";
  public static final String DUE_DATE = "dueDate";
  public static final String CANDIDATE_GROUPS = "candidateGroups";
  public static final String CANDIDATE_USERS = "candidateUsers";
  public static final String FORM_KEY = "formKey";
  public static final String FOLLOW_UP_DATE = "followUpDate";
  public static final String EXTERNAL_FORM_REFERENCE = "externalFormReference";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";
  public static final String CUSTOM_HEADERS = "customHeaders";
  public static final String VARIABLE_SCOPE_KEY = "scopeKey";
  public static final String PRIORITY = "priority";
  public static final String DATA_TYPE = "dataType";
  public static final String JOIN_FIELD_NAME = "join";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  private static Optional<String> getElsFieldByGraphqlField(final String fieldName) {
    switch (fieldName) {
      case ("id"):
        return of(ID);
      case ("key"):
        return of(USER_TASK_KEY);
      case ("taskId"):
        return of(TASK_ID);
      case ("name"):
        return of(VARIABLE_NAME);
      case ("value"):
        return of(VARIABLE_FULL_VALUE);
      case ("isPreview"):
        return of(IS_PREVIEW);
      case ("flowNodeBpmnId"):
        return of(FLOW_NODE_BPMN_ID);
      case ("flowNodeInstanceId"):
        return of(FLOW_NODE_INSTANCE_ID);
      case ("partitionId"):
        return of(PARTITION_ID);
      case ("completionTime"):
        return of(COMPLETION_TIME);
      case ("processInstanceId"):
        return of(PROCESS_INSTANCE_ID);
      case ("position"):
        return of(POSITION);
      case ("state"):
        return of(STATE);
      case ("creationTime"):
        return of(CREATION_TIME);
      case ("bpmnProcessId"):
        return of(BPMN_PROCESS_ID);
      case ("processDefinitionId"):
        return of(PROCESS_DEFINITION_ID);
      case ("assignee"):
        return of(ASSIGNEE);
      case ("candidateGroups"):
        return of(CANDIDATE_GROUPS);
      case ("candidateUsers"):
        return of(CANDIDATE_USERS);
      case ("formKey"):
        return of(FORM_KEY);
      case ("followUpDate"):
        return of(FOLLOW_UP_DATE);
      case ("dueDate"):
        return of(DUE_DATE);
      case ("externalFormReference"):
        return of(EXTERNAL_FORM_REFERENCE);
      case ("processDefinitionVersion"):
        return of(PROCESS_DEFINITION_VERSION);
      case ("customHeaders"):
        return of(CUSTOM_HEADERS);
      case ("priority"):
        return of(PRIORITY);
      case ("scopeKey"):
        return of(VARIABLE_SCOPE_KEY);
      case ("dataType"):
        return of(DATA_TYPE);
      case ("join"):
        return of(JOIN_FIELD_NAME);
      default:
        return empty();
    }
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }
}
