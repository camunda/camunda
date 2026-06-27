/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

public enum ContextType {
  // delete+insert is used for AGENT_INSTANCE_ELEMENT_INSTANCE child rows, so order must be
  // preserved to ensure DELETE runs before INSERT
  AGENT_INSTANCE(true),
  // only INSERT (CREATED) and UPDATE (COMMITTED/DISCARDED) are issued — no delete+insert pattern;
  // INSERT-before-UPDATE ordering within a batch is guaranteed by optimizeQueueOrder's
  // WriteStatementType sort, so no explicit preservation is needed here
  AGENT_HISTORY(false),
  AUDIT_LOG(false),
  AUTHORIZATION(true),
  BATCH_OPERATION(false),
  CORRELATED_MESSAGE_SUBSCRIPTION(false),
  DECISION_DEFINITION(false),
  DECISION_INSTANCE(false),
  EXPORTER_POSITION(false),
  FLOW_NODE(false),
  FORM(false),
  GROUP(false),
  HISTORY_DELETION(false),
  INCIDENT(false),
  JOB(false),
  JOB_METRICS_BATCH(false),
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
  VARIABLE(false),
  WAIT_STATE(false),
  CLUSTER_VARIABLE(false),
  PROCESS_DEF_VAR_NAME_LOOKUP(false),
  // for global listeners, event types are updated through delete+insert, so order needs to be
  // preserved
  GLOBAL_LISTENER(true),
  DEPLOYED_RESOURCE(false);

  private final boolean preserveOrder;

  ContextType(final boolean preserveOrder) {
    this.preserveOrder = preserveOrder;
  }

  public boolean preserveOrder() {
    return preserveOrder;
  }
}
