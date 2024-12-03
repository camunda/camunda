/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

public enum ImportValueTypes {
  PROCESS_INSTANCE("process-instance"),
  DECISION("decision"),
  DECISION_REQUIREMENTS("decision-requirements"),
  DECISION_EVALUATION("decision-evaluation"),
  JOB("job"),
  INCIDENT("incident"),
  PROCESS("process"),
  VARIABLE("variable"),
  VARIABLE_DOCUMENT("variable-document"),
  PROCESS_MESSAGE_SUBSCRIPTION("process-message-subscription"),
  USER_TASK("user-task"),
  FORM("form");

  private final String aliasTemplate;

  ImportValueTypes(final String aliasTemplate) {
    this.aliasTemplate = aliasTemplate;
  }

  public String getAliasTemplate() {
    return aliasTemplate;
  }
}
