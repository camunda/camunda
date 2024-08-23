/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.license;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is a duplicate of src/main/java/io/camunda/service/license/LicenseType.java
 *
 * <p>This class exists because Optimize is not part of the single application, and cannot use any
 * of the monorepo's modules. Once Optimize is added, the `service` implementation of `LicenseType`
 * can be used, and this Optimize duplicate can be removed
 */
public enum LicenseType {
  SAAS("saas"),
  PRODUCTION("production"),
  UNKNOWN("unknown");
  private static final Map<String, LicenseType> ENUM_MAP;

  static {
    final Map<String, LicenseType> map = new HashMap<>();
    for (final LicenseType instance : LicenseType.values()) {
      map.put(instance.getName().toLowerCase(), instance);
    }
    ENUM_MAP = Collections.unmodifiableMap(map);
  }

  private final String name;

  LicenseType(final String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public static LicenseType get(final String name) {
    if (name == null) {
      return LicenseType.UNKNOWN;
    }
    return ENUM_MAP.getOrDefault(name.toLowerCase(), LicenseType.UNKNOWN);
  }
}
