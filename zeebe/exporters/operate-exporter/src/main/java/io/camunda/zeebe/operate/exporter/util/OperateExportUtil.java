/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.util;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import java.time.OffsetDateTime;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperateExportUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperateExportUtil.class);

  public static String tenantOrDefault(final String tenantId) {
    if (StringUtils.isEmpty(tenantId)) {
      return DEFAULT_TENANT_ID;
    }
    return tenantId;
  }

  public static OffsetDateTime toDateOrNull(final String dateString) {
    if (dateString == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(dateString);
    } catch (final Exception e) {
      LOGGER.warn("Could not parse {} as OffsetDateTime. Use null.", dateString);
      return null;
    }
  }

  public static String trimWhitespace(String str) {
    return (str == null) ? null : str.strip();
  }
}
