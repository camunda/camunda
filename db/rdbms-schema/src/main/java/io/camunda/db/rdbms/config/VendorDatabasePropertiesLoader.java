/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.config;

import java.io.IOException;
import java.util.Properties;

public final class VendorDatabasePropertiesLoader {

  private VendorDatabasePropertiesLoader() {}

  public static VendorDatabaseProperties load(final String databaseId) throws IOException {
    final Properties properties = new Properties();
    final var file = "db/vendor-properties/" + databaseId + ".properties";
    try (final var propertiesInputStream =
        VendorDatabasePropertiesLoader.class.getClassLoader().getResourceAsStream(file)) {
      if (propertiesInputStream != null) {
        properties.load(propertiesInputStream);
      } else {
        throw new IllegalArgumentException(
            "No vendor properties found for databaseId " + databaseId);
      }
    }

    return new VendorDatabaseProperties(properties);
  }
}
