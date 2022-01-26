/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "decision-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String STATE = "state";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  //TODO more fields will be added when needed

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

}
