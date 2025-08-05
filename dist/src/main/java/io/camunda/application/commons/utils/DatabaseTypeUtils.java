/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.utils;

import java.util.Optional;
import org.springframework.core.env.Environment;

public final class DatabaseTypeUtils {
  public static final String PROPERTY_CAMUNDA_DATABASE_TYPE = "camunda.database.type";
  public static final String UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE =
      "camunda.data.secondary-storage.type";

  private DatabaseTypeUtils() {}

  public static boolean isSecondaryStorageEnabled(final Environment env) {
    String dbType = env.getProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE);
    if (dbType == null) {
      dbType =
          Optional.ofNullable(env.getProperty(PROPERTY_CAMUNDA_DATABASE_TYPE))
              .orElse("elasticsearch");
    }
    return !"none".equalsIgnoreCase(dbType);
  }
}
