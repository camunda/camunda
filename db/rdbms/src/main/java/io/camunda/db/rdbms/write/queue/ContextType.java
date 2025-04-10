/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

public enum ContextType {
  AUTHORIZATION(true),
  EXPORTER_POSITION(false),
  DECISION_DEFINITION(false),
  DECISION_INSTANCE(false),
  GROUP(false),
  PROCESS_DEFINITION(false),
  PROCESS_INSTANCE(false),
  FLOW_NODE(false),
  TENANT(false),
  INCIDENT(false),
  VARIABLE(false),
  ROLE(false),
  USER(false),
  USER_TASK(true),
  FORM(false),
  MAPPING(false),
  BATCH_OPERATION(false),
  JOB(false);

  private final boolean preserveOrder;

  ContextType(final boolean preserveOrder) {
    this.preserveOrder = preserveOrder;
  }

  public boolean preserveOrder() {
    return preserveOrder;
  }
}
