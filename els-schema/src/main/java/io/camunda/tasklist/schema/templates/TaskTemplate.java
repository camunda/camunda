/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.templates;

import io.camunda.tasklist.schema.backup.Prio2Backup;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import org.springframework.stereotype.Component;

@Component
public class TaskTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio2Backup {

  public static final String INDEX_NAME = "task";

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
  public static final String FOLLOW_UP_DATE = "followUpDate";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.2.2";
  }

  @Override
  public String getAllIndicesPattern() {
    return super.getIndexPattern();
  }
}
