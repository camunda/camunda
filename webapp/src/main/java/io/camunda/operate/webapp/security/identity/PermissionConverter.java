/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.operate.webapp.security.Permission;
import org.springframework.core.convert.converter.Converter;

public final class PermissionConverter implements Converter<String, Permission> {

  public static final String READ_PERMISSION_VALUE = "read:*";
  public static final String WRITE_PERMISSION_VALUE = "write:*";

  private static volatile PermissionConverter _instance;
  private static Object lock = new Object();

  private PermissionConverter() {
    //private constructor
  }

  public static PermissionConverter getInstance(){
    if (_instance == null) {
      synchronized (lock) {
        if (_instance == null) {
          _instance = new PermissionConverter();
        }
      }
    }
    return _instance;
  }

  @Override
  public Permission convert(final String source) {
    if (source.equals(READ_PERMISSION_VALUE)) {
      return Permission.READ;
    }
    if (source.equals(WRITE_PERMISSION_VALUE)) {
      return Permission.WRITE;
    }
    return null;
  }
}
