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
  BATCH_OPERATION(false),
  CORRELATED_MESSAGE(false),
  DECISION_DEFINITION(false),
  DECISION_INSTANCE(false),
  EXPORTER_POSITION(false),
  FLOW_NODE(false),
  FORM(false),
  GROUP(false),
  INCIDENT(false),
  JOB(false),
  MAPPING_RULE(false),
  MESSAGE_SUBSCRIPTION(false),
  PROCESS_DEFINITION(false),
  PROCESS_INSTANCE(false),
  ROLE(false),
  SEQUENCE_FLOW(false),
  TENANT(false),
  USAGE_METRIC(false),
  USAGE_METRIC_TU(false),
  USER(false),
  USER_TASK(true),
  VARIABLE(false);

  private final boolean preserveOrder;

  ContextType(final boolean preserveOrder) {
    this.preserveOrder = preserveOrder;
  }

  public boolean preserveOrder() {
    return preserveOrder;
  }
}
