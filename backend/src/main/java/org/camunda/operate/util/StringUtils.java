/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

public class StringUtils {

  public static String toStringOrNull(Object object) {
    return toStringOrDefault(object, null);
  }
  
  public static String toStringOrDefault(Object object,String defaultString) {
    return object == null ? defaultString : object.toString();
  }
}
