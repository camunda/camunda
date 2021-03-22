/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class WorkflowIndex extends AbstractIndexDescriptor {

  public static final String INDEX_NAME = "workflow";
  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String NAME = "name";
  public static final String FLOWNODES = "flowNodes";
  public static final String FLOWNODE_ID = "id";
  public static final String FLOWNODE_NAME = "name";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}
