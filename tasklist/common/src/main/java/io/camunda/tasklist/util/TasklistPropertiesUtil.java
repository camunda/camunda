/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

public final class TasklistPropertiesUtil {

  public static final String DATABASE_PROPERTY_NAME = "camunda.tasklist.database";
  private static volatile Boolean isOpenSearchDatabaseCache = null;

  private TasklistPropertiesUtil() {
    /*utility class*/
  }

  public static boolean isOpenSearchDatabase() {
    if (isOpenSearchDatabaseCache == null) {
      synchronized (TasklistPropertiesUtil.class) {
        if (isOpenSearchDatabaseCache == null) {
          isOpenSearchDatabaseCache = "opensearch".equalsIgnoreCase(getTasklistDatabase());
        }
      }
    }
    return Boolean.TRUE.equals(isOpenSearchDatabaseCache);
  }

  private static String getTasklistDatabase() {
    return SpringContextHolder.getProperty(DATABASE_PROPERTY_NAME);
  }
}
