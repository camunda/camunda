/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import io.camunda.tasklist.webapp.security.Permission;
import org.springframework.core.convert.converter.Converter;

public final class PermissionConverter implements Converter<String, Permission> {

  public static final String READ_PERMISSION_VALUE = "read:*";
  public static final String WRITE_PERMISSION_VALUE = "write:*";

  private static volatile PermissionConverter instance;
  private static Object lock = new Object();

  private PermissionConverter() {
    // private constructor
  }

  public static PermissionConverter getInstance() {
    if (instance == null) {
      synchronized (lock) {
        if (instance == null) {
          instance = new PermissionConverter();
        }
      }
    }
    return instance;
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
