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

public class RdbmsPurger {

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

    for (final String tableName : RdbmsTableNames.TABLE_NAMES) {
      purgeMapper.truncateTable(tableName);
    }
    if (vendorDatabaseProperties.disableFkBeforeTruncate()) {
      purgeMapper.enableForeignKeyChecks();
    }
  }
}
