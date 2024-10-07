/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.util;

import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;

import org.springframework.util.StringUtils;

public abstract class ImportUtil {

  public static String tenantOrDefault(final String tenantId) {
    if (!StringUtils.hasLength(tenantId)) {
      return DEFAULT_TENANT_ID;
    }
    return tenantId;
  }
}
