/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
 * contributor license agreements. Licensed under a proprietary license. See the License.txt file
 * for more information. You may not use this file except in compliance with the proprietary
 * license.
 */
package io.camunda.zeebe.exporter.operate.schema.templates;

import io.camunda.operate.schema.backup.Prio3Backup;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;

public class IncidentTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "incident";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";
  public static final String JOB_KEY = "jobKey";
  public static final String ERROR_TYPE = "errorType";
  public static final String ERROR_MSG = "errorMessage";
  public static final String ERROR_MSG_HASH = "errorMessageHash";
  public static final String STATE = "state";
  public static final String CREATION_TIME = "creationTime";
  public static final String TREE_PATH = "treePath";

  public IncidentTemplate(String indexPrefix) {
    super(indexPrefix);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  // we have to use version 8.3.1 here, as we mistakenly released 8.3.0 index version in Operate
  // 8.2.6
  public String getVersion() {
    return "8.3.1";
  }
}
