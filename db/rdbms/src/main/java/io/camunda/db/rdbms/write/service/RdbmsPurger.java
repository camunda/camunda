/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.RdbmsTableNames;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.sql.PurgeMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

public class RdbmsPurger {
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
        for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
          // Purge exported data, but retain metadata describing the schema that remains in place.
          if (RdbmsTableNames.SCHEMA_VERSION.equals(tableName)) {
            continue;
          }
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
