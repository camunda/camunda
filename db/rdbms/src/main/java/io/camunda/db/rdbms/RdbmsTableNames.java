/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import java.util.List;

/**
 * Contains the list of all known RDBMS table names. This list is used by various components such as
 * the purger and metrics.
 */
public final class RdbmsTableNames {

  /**
   * List of all known RDBMS table names. The order of tables in this list will be used by the
   * RdbmsPurger to determine the order in which the tables are truncated. It is important to always
   * truncate source tables of a FK reference before the target table of a FK reference is
   * truncated. Failure to do so will result in FK constraint violations during truncation. Easiest
   * symptom is a flaky test RdbmsPurgerIT in the CI pipeline.
   */
  public static final List<String> TABLE_NAMES =
      List.of(
          "AUDIT_LOG",
          "AUTHORIZATIONS",
          "BATCH_OPERATION_ITEM",
          "BATCH_OPERATION_ERROR",
          "BATCH_OPERATION",
          "CANDIDATE_GROUP",
          "CANDIDATE_USER",
          "CLUSTER_VARIABLE",
          "CORRELATED_MESSAGE_SUBSCRIPTION",
          "DECISION_DEFINITION",
          "DECISION_INSTANCE_INPUT",
          "DECISION_INSTANCE_OUTPUT",
          "DECISION_INSTANCE",
          "DECISION_REQUIREMENTS",
          "EXPORTER_POSITION",
          "FLOW_NODE_INSTANCE",
          "FORM",
          "GLOBAL_LISTENER",
          "GLOBAL_LISTENER_EVENT_TYPE",
          "GROUP_MEMBER",
          "GROUP_",
          "HISTORY_DELETION",
          "INCIDENT",
          "JOB",
          "JOB_METRICS_BATCH",
          "MAPPING_RULES",
          "MESSAGE_SUBSCRIPTION",
          "PROCESS_DEFINITION",
          "PROCESS_INSTANCE_TAG",
          "PROCESS_INSTANCE",
          "ROLE_MEMBER",
          "ROLES",
          "SEQUENCE_FLOW",
          "TENANT_MEMBER",
          "TENANT",
          "USAGE_METRIC",
          "USAGE_METRIC_TU",
          "USER_TASK_TAG",
          "USER_TASK",
          "USER_",
          "VARIABLE",
          "WEB_SESSION");

  private RdbmsTableNames() {
    // Utility class - prevent instantiation
  }
}
