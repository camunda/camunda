/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.PurgeMapper;
import java.util.List;

public class RdbmsPurger {

  private static final List<String> TABLE_NAMES =
      List.of(
          "EXPORTER_POSITION",
          "VARIABLE",
          "FLOW_NODE_INSTANCE",
          "PROCESS_INSTANCE",
          "PROCESS_DEFINITION",
          "CANDIDATE_USER",
          "CANDIDATE_GROUP",
          "USER_TASK",
          "INCIDENT",
          "DECISION_INSTANCE_INPUT",
          "DECISION_INSTANCE_OUTPUT",
          "DECISION_INSTANCE",
          "DECISION_DEFINITION",
          "DECISION_REQUIREMENTS",
          "AUTHORIZATIONS",
          "USERS",
          "FORM",
          "MAPPINGS",
          "TENANT_MEMBER",
          "TENANT",
          "ROLE_MEMBER",
          "ROLES",
          "GROUP_MEMBER",
          "GROUPS");

  private final PurgeMapper purgeMapper;

  public RdbmsPurger(final PurgeMapper purgeMapper) {
    this.purgeMapper = purgeMapper;
  }

  public void purgeRdbms() {
    for (final String tableName : TABLE_NAMES) {
      purgeMapper.truncateTable(tableName);
    }
  }
}
