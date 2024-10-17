/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.zeebe.protocol.record.value.TenantOwned;

public final class ExporterUtil {

  private ExporterUtil() {
    // utility class
  }

  public static String tenantOrDefault(final String tenantId) {
    if (tenantId == null || tenantId.isEmpty()) {
      return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    }
    return tenantId;
  }

  public static String toStringOrNull(final Object object) {
    return toStringOrDefault(object, null);
  }

  public static String toStringOrDefault(final Object object, final String defaultString) {
    return object == null ? defaultString : object.toString();
  }

  public static String trimWhitespace(final String str) {
    return (str == null) ? null : str.strip();
  }
}
