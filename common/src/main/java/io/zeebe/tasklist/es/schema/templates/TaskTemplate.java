/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.es.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class TaskTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "task";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String START_DATE = "startDate";
  public static final String CREATION_TIME = "creationTime";
  public static final String COMPLETION_TIME = "completionTime";
  public static final String ELEMENT_ID = "elementId";
  public static final String STATE = "state";

  @Override
  protected String getIndexNameFormat() {
    return INDEX_NAME;
  }

}