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
  AGENT_DEFINITION(false),
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
  // the suspend/resume restore re-emits CREATED (createIfNotExists, an INSERT-typed upsert) on a
  // row that may already be CORRELATED (an UPDATE). The default INSERT-before-UPDATE sort would run
  // the restore-CREATED upsert before an earlier-queued CORRELATED update in the same flush,
  // leaving
  // the row stale as CORRELATED. Preserve insertion order so the later engine event wins; the
  // normal
  // create-before-update order is insertion order too, so it stays correct.
  MESSAGE_SUBSCRIPTION(true),
  // draining-deletion issues markDraining and markDeleted as two UPDATEs on the same row, their
  // order must be preserved so the state is updated correctly
  PROCESS_DEFINITION(true),
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
  // delete+insert is used for CLUSTER_VARIABLE_METADATA child rows, so order must be preserved
  // to ensure DELETE runs before INSERT
  CLUSTER_VARIABLE(true),
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
