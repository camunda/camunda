/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.queue;

public enum ContextType {
  AUDIT_LOG,
  AUTHORIZATION(QueueOrdering.PRESERVE),
  BATCH_OPERATION,
  CORRELATED_MESSAGE_SUBSCRIPTION,
  DECISION_DEFINITION,
  DECISION_INSTANCE,
  EXPORTER_POSITION,
  FLOW_NODE,
  FORM,
  // identity entities can be created, deleted and re-created with the same ID within one flush;
  // the default sort would run both INSERTs before the DELETE and violate the primary key
  GROUP(QueueOrdering.PRESERVE),
  HISTORY_DELETION,
  INCIDENT,
  JOB,
  JOB_METRICS_BATCH,
  MAPPING_RULE(QueueOrdering.PRESERVE),
  MESSAGE_SUBSCRIPTION,
  // draining-deletion issues markDraining and markDeleted as two UPDATEs on the same row, their
  // order must be preserved so the state is updated correctly
  PROCESS_DEFINITION(QueueOrdering.PRESERVE),
  PROCESS_INSTANCE,
  ROLE(QueueOrdering.PRESERVE),
  SEQUENCE_FLOW,
  TENANT(QueueOrdering.PRESERVE),
  USAGE_METRIC,
  USAGE_METRIC_TU,
  USER(QueueOrdering.PRESERVE),
  // all statements are keyed by the user task key and only touch that task's rows, so the order
  // only has to hold per key; this lets statements of different user tasks be batched
  USER_TASK(QueueOrdering.PRESERVE_PER_ID),
  VARIABLE,
  CLUSTER_VARIABLE,
  // for global listeners, event types are updated through delete+insert, so order needs to be
  // preserved
  GLOBAL_LISTENER(QueueOrdering.PRESERVE);

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
