/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebe;

@SuppressWarnings("checkstyle:InterfaceIsType")
public interface ZeebeESConstants {

  String POSITION_FIELD_NAME = "position";
  String WORKFLOW_INSTANCE_INDEX_NAME = "workflow-instance";
  String JOB_INDEX_NAME = "job";
  String DEPLOYMENT_INDEX_NAME = "deployment";
  String VARIABLE_INDEX_NAME = "variable";
  String VARIABLE_DOCUMENT_INDEX_NAME = "variable-document";
}
