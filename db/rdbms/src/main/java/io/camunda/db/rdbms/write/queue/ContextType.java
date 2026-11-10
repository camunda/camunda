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
  AGENT_INSTANCE(QueueOrdering.PRESERVE),
  // only INSERT (CREATED) and UPDATE (COMMITTED/DISCARDED) are issued — no delete+insert pattern;
  // INSERT-before-UPDATE ordering within a batch is guaranteed by optimizeQueueOrder's
  // WriteStatementType sort, so no explicit preservation is needed here
  AGENT_HISTORY,
  AGENT_DEFINITION,
  AUDIT_LOG,
  AUTHORIZATION(QueueOrdering.PRESERVE),
  BATCH_OPERATION,
  CORRELATED_MESSAGE_SUBSCRIPTION,
  DECISION_DEFINITION,
  DECISION_INSTANCE,
  EXPORTER_POSITION,
  FLOW_NODE,
  FORM,
  GROUP,
  HISTORY_DELETION,
  INCIDENT,
  JOB,
  JOB_METRICS_BATCH,
  MAPPING_RULE,
  // suspend/resume can queue a CORRELATED update then a restore-CREATED upsert for the same row in
  // one flush; the default sort would run CREATED first and leave the row stale as CORRELATED
  MESSAGE_SUBSCRIPTION(QueueOrdering.PRESERVE),
  // draining-deletion issues markDraining and markDeleted as two UPDATEs on the same row, their
  // order must be preserved so the state is updated correctly
  PROCESS_DEFINITION(QueueOrdering.PRESERVE),
  PROCESS_INSTANCE,
  ROLE,
  SEQUENCE_FLOW,
  TENANT,
  USAGE_METRIC,
  USAGE_METRIC_TU,
  USER,
  // all statements are keyed by the user task key and only touch that task's rows, so the order
  // only has to hold per key; this lets statements of different user tasks be batched
  USER_TASK(QueueOrdering.PRESERVE_PER_ID),
  VARIABLE,
  WAIT_STATE,
  // delete+insert is used for CLUSTER_VARIABLE_METADATA child rows, so order must be preserved
  // to ensure DELETE runs before INSERT
  CLUSTER_VARIABLE(QueueOrdering.PRESERVE),
  PROCESS_DEF_VAR_NAME_LOOKUP,
  // for global listeners, event types are updated through delete+insert, so order needs to be
  // preserved
  GLOBAL_LISTENER(QueueOrdering.PRESERVE),
  DEPLOYED_RESOURCE;

  private final QueueOrdering ordering;

  ContextType() {
    this(QueueOrdering.SORT_BY_STATEMENT_TYPE);
  }

  ContextType(final QueueOrdering ordering) {
    this.ordering = ordering;
  }

  public QueueOrdering ordering() {
    return ordering;
  }
}
