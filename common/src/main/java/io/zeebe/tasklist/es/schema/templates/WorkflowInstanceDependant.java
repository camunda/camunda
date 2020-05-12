/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es.schema.templates;

public interface WorkflowInstanceDependant {

  String WORKFLOW_INSTANCE_KEY = "workflowInstanceKey";

  String getMainIndexName();

}
