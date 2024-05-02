/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

public final class TasklistPropertiesUtil {

  private static final String DATABASE_PROPERTY_NAME = "camunda.tasklist.database";

  private TasklistPropertiesUtil() {
    /*utility class*/
  }

  public static String getTasklistDatabase() {
    return System.getProperty(DATABASE_PROPERTY_NAME, System.getenv(DATABASE_PROPERTY_NAME));
  }

  public static boolean isOpenSearchDatabase() {
    return "opensearch".equalsIgnoreCase(TasklistPropertiesUtil.getTasklistDatabase());
  }
}
