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
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public class RdbmsPurger {

  /** Do not change the order here !! */
  public static final List<String> TABLE_NAMES =
      List.of(
          "AUTHORIZATIONS",
          "BATCH_OPERATION_ITEM",
          "BATCH_OPERATION_ERROR",
          "BATCH_OPERATION",
          "CANDIDATE_GROUP",
          "CANDIDATE_USER",
          "CORRELATED_MESSAGE_SUBSCRIPTION",
          "DECISION_DEFINITION",
          "DECISION_INSTANCE_INPUT",
          "DECISION_INSTANCE_OUTPUT",
          "DECISION_INSTANCE",
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
          "TENANT_MEMBER",
          "TENANT",
          "USAGE_METRIC",
          "USAGE_METRIC_TU",
          "USER_TASK",
          "USERS",
          "VARIABLE");

  private final SqlSessionFactory sqlSessionFactory;
  private final VendorDatabaseProperties vendorDatabaseProperties;

  public RdbmsPurger(
      final SqlSessionFactory sqlSessionFactory,
      final VendorDatabaseProperties vendorDatabaseProperties) {
    this.sqlSessionFactory = sqlSessionFactory;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
  }

  public void purgeRdbms() {
    // One session, so the foreign key setting (a per-connection setting on MySQL/MariaDB) applies
    // to the truncates and is restored on the same connection before it goes back to the pool.
    try (final SqlSession session = sqlSessionFactory.openSession()) {
      final PurgeMapper purgeMapper = session.getMapper(PurgeMapper.class);
      final boolean disableForeignKeys = vendorDatabaseProperties.disableFkBeforeTruncate();
      if (disableForeignKeys) {
        purgeMapper.disableForeignKeyChecks();
      }
      try {
        for (final String tableName : TABLE_NAMES) {
          purgeMapper.truncateTable(tableName);
        }
      } finally {
        if (disableForeignKeys) {
          purgeMapper.enableForeignKeyChecks();
        }
      }
      session.commit();
    }
  }
}
