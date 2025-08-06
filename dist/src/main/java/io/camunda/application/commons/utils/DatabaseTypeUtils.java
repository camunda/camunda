/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.utils;

import org.springframework.core.env.Environment;

public final class DatabaseTypeUtils {
  public static final String PROPERTY_CAMUNDA_DATABASE_TYPE = "camunda.database.type";

  private DatabaseTypeUtils() {}

  public static boolean isSecondaryStorageEnabled(final Environment env) {
    final String dbType = env.getProperty(PROPERTY_CAMUNDA_DATABASE_TYPE);
    return !"none".equalsIgnoreCase(dbType);
  }
}
