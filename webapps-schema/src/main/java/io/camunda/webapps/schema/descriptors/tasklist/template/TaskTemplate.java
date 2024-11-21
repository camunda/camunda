/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.tasklist.template;

import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.tasklist.TasklistTemplateDescriptor;

public class TaskTemplate extends TasklistTemplateDescriptor
    implements Prio3Backup, ProcessInstanceDependant {

  public static final String INDEX_NAME = "task";
  public static final String INDEX_VERSION = "8.5.0";
  public static final String COMPONENT_NAME = "tasklist";

  /* User Task Fields*/
  public static final String ID = "id";
  public static final String KEY = "key";

  public static final String CREATION_TIME = "creationTime";
  public static final String COMPLETION_TIME = "completionTime";
  public static final String DUE_DATE = "dueDate";
  public static final String FOLLOW_UP_DATE = "followUpDate";

  public static final String STATE = "state";
  public static final String IMPLEMENTATION = "implementation";
  public static final String ASSIGNEE = "assignee";
  public static final String CANDIDATE_GROUPS = "candidateGroups";
  public static final String CANDIDATE_USERS = "candidateUsers";
  public static final String CUSTOM_HEADERS = "customHeaders";
  public static final String PRIORITY = "priority";
  public static final String ACTION = "action";
  public static final String CHANGED_ATTRIBUTES = "changedAttributes";

  public static final String FORM_KEY = "formKey";
  public static final String EXTERNAL_FORM_REFERENCE = "externalFormReference";

  public static final String TENANT_ID = "tenantId";
  public static final String PARTITION_ID = "partitionId";
  public static final String POSITION = "position";

  /* Variable Fields */
  public static final String VARIABLE_NAME = "name";
  public static final String VARIABLE_VALUE = "value";
  public static final String VARIABLE_FULL_VALUE = "fullValue";
  public static final String IS_TRUNCATED = "isTruncated";
  public static final String VARIABLE_SCOPE_KEY = "scopeKey";

  /* Process Information Fields */
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String FLOW_NODE_BPMN_ID = "flowNodeBpmnId";
  public static final String FLOW_NODE_INSTANCE_ID = "flowNodeInstanceId";
  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";

  public static final String JOIN_FIELD_NAME = "join";

  public TaskTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getProcessInstanceDependantField() {
    return PROCESS_INSTANCE_ID;
  }

  public static String getIndexNameWithPrefix(final String prefix) {
    return new TaskTemplate(prefix, false).getFullQualifiedName();
  }
}
