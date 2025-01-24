/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rdbms;

import java.util.Properties;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;

public class RdbmsDatabaseIdProvider extends VendorDatabaseIdProvider {

  private static final Properties VENDOR_PROPERTIES = new Properties();

  static {
    VENDOR_PROPERTIES.put("H2", "h2");
    VENDOR_PROPERTIES.put("PostgreSQL", "postgresql");
    VENDOR_PROPERTIES.put("Oracle", "oracle");
    VENDOR_PROPERTIES.put("MariaDB", "mariadb");
    VENDOR_PROPERTIES.put("MySQL", "mariadb");
  }

  private final String databaseIdOverride;

  public RdbmsDatabaseIdProvider(final String databaseIdOverride) {
    this.databaseIdOverride = databaseIdOverride;
    setProperties(VENDOR_PROPERTIES);
  }

  @Override
  public String getDatabaseId(final DataSource dataSource) {
    if (databaseIdOverride != null && !databaseIdOverride.isBlank()) {
      if (VENDOR_PROPERTIES.containsValue(databaseIdOverride)) {
        return databaseIdOverride;
      } else {
        throw new IllegalArgumentException(
            "Invalid databaseIdOverride '"
                + databaseIdOverride
                + "', must be one of "
                + VENDOR_PROPERTIES.values());
      }
    }

    final var vendorId = super.getDatabaseId(dataSource);
    if (vendorId == null) {
      throw new IllegalArgumentException("Unable to detect database vendor");
    }

    return vendorId;
  }
}
