/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

public class ExporterUtils {
  public static final String DEFAULT_TENANT_ID = "<default>";

  public static String tenantOrDefault(final String tenantId) {
    if (tenantId == null || tenantId.isEmpty()) {
      return DEFAULT_TENANT_ID;
    }
    return tenantId;
  }
}
