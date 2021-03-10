/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class IncidentTemplate extends AbstractTemplateDescriptor implements WorkflowInstanceDependant {

  public static final String INDEX_NAME = "incident";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";

  public static final String WORKFLOW_KEY = "workflowKey";
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";
  public static final String JOB_KEY = "jobKey";
  public static final String ERROR_TYPE = "errorType";
  public static final String ERROR_MSG = "errorMessage";
  public static final String ERROR_MSG_HASH = "errorMessageHash";
  public static final String STATE = "state";
  public static final String CREATION_TIME = "creationTime";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

}
