/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class WorkflowIndex extends AbstractIndexDescriptor {

  public static final String INDEX_NAME = "workflow";
  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String NAME = "name";
  public static final String VERSION = "version";
  public static final String BPMN_XML = "bpmnXml";
  public static final String RESOURCE_NAME = "resourceName";
  public static final String ACTIVITIES = "activities";
  public static final String ACTIVITY_NAME = "name";
  public static final String ACTIVITY_TYPE = "type";

  @Override
  protected String getMainIndexName() {
    return INDEX_NAME;
  }

}
