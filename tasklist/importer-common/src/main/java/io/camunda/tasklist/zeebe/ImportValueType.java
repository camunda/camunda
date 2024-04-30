/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebe;

public enum ImportValueType {
  PROCESS_INSTANCE(ZeebeESConstants.PROCESS_INSTANCE_INDEX_NAME),
  JOB(ZeebeESConstants.JOB_INDEX_NAME),
  PROCESS(ZeebeESConstants.PROCESS_INDEX_NAME),
  VARIABLE(ZeebeESConstants.VARIABLE_INDEX_NAME),
  FORM(ZeebeESConstants.FORM_INDEX_NAME),
  USER_TASK(ZeebeESConstants.USER_TASK_INDEX_NAME);

  private final String aliasTemplate;

  ImportValueType(String aliasTemplate) {
    this.aliasTemplate = aliasTemplate;
  }

  public String getAliasTemplate() {
    return aliasTemplate;
  }

  public String getIndicesPattern(String prefix) {
    return String.format("%s*%s*", prefix, aliasTemplate);
  }

  public String getAliasName(String prefix) {
    return String.format("%s-%s", prefix, aliasTemplate);
  }
}
