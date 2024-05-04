/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebe;

public enum ImportValueType {
  PROCESS_INSTANCE(ZeebeESConstants.PROCESS_INSTANCE_INDEX_NAME),
  DECISION(ZeebeESConstants.DECISION_INDEX_NAME),
  DECISION_REQUIREMENTS(ZeebeESConstants.DECISION_REQUIREMENTS_INDEX_NAME),
  DECISION_EVALUATION(ZeebeESConstants.DECISION_EVALUATION_INDEX_NAME),
  JOB(ZeebeESConstants.JOB_INDEX_NAME),
  INCIDENT(ZeebeESConstants.INCIDENT_INDEX_NAME),
  PROCESS(ZeebeESConstants.PROCESS_INDEX_NAME),
  VARIABLE(ZeebeESConstants.VARIABLE_INDEX_NAME),
  VARIABLE_DOCUMENT(ZeebeESConstants.VARIABLE_DOCUMENT_INDEX_NAME),
  PROCESS_MESSAGE_SUBSCRIPTION(ZeebeESConstants.PROCESS_MESSAGE_SUBSCRIPTION_INDEX_NAME),
  USER_TASK(ZeebeESConstants.USER_TASK_INDEX_NAME);

  public static final ImportValueType[] IMPORT_VALUE_TYPES =
      new ImportValueType[] {
        PROCESS,
        DECISION,
        DECISION_REQUIREMENTS,
        DECISION_EVALUATION,
        PROCESS_INSTANCE,
        JOB,
        INCIDENT,
        VARIABLE,
        VARIABLE_DOCUMENT,
        PROCESS_MESSAGE_SUBSCRIPTION,
        USER_TASK
      };
  private final String aliasTemplate;

  ImportValueType(final String aliasTemplate) {
    this.aliasTemplate = aliasTemplate;
  }

  public String getAliasTemplate() {
    return aliasTemplate;
  }

  public String getIndicesPattern(final String prefix) {
    return String.format("%s*%s*", prefix, aliasTemplate);
  }

  public String getAliasName(final String prefix) {
    return String.format("%s-%s", prefix, aliasTemplate);
  }
}
