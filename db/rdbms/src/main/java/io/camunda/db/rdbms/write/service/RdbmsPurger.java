/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.PurgeMapper;
import java.util.List;

public class RdbmsPurger {

  private static final List<String> TABLE_NAMES =
      List.of(
          "AUTHORIZATIONS",
          "BATCH_OPERATION",
          "BATCH_OPERATION_ERROR",
          "BATCH_OPERATION_ITEM",
          "CANDIDATE_GROUP",
          "CANDIDATE_USER",
          "CORRELATED_MESSAGE",
          "DECISION_DEFINITION",
          "DECISION_INSTANCE",
          "DECISION_INSTANCE_INPUT",
          "DECISION_INSTANCE_OUTPUT",
          "DECISION_REQUIREMENTS",
          "EXPORTER_POSITION",
          "FLOW_NODE_INSTANCE",
          "FORM",
          "GROUP_MEMBER",
          "GROUPS",
          "INCIDENT",
          "JOB",
          "MAPPING_RULES",
          "MESSAGE_SUBSCRIPTION",
          "PROCESS_DEFINITION",
          "PROCESS_INSTANCE",
          "ROLE_MEMBER",
          "ROLES",
          "SEQUENCE_FLOW",
          "TENANT",
          "TENANT_MEMBER",
          "USAGE_METRIC",
          "USAGE_METRIC_TU",
          "USER_TASK",
          "USERS",
          "VARIABLE");

  private final PurgeMapper purgeMapper;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public RdbmsPurger(
      final PurgeMapper purgeMapper, final VendorDatabaseProperties vendorDatabaseProperties) {
    this.purgeMapper = purgeMapper;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void purgeRdbms() {
    if (vendorDatabaseProperties.disableFkBeforeTruncate()) {
      purgeMapper.disableForeignKeyChecks();
    }

    for (final String tableName : TABLE_NAMES) {
      purgeMapper.truncateTable(tableName);
    }
    if (vendorDatabaseProperties.disableFkBeforeTruncate()) {
      purgeMapper.enableForeignKeyChecks();
    }
  }
}
