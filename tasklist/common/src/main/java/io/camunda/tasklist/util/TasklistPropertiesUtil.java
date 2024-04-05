/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
