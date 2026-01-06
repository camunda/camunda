/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.search.entities.BatchOperationType;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExporterUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExporterUtil.class);

  private ExporterUtil() {
    // utility class
  }

  public static String tenantOrDefault(final String tenantId) {
    if (tenantId == null || tenantId.isEmpty()) {
      return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
    }
    return tenantId;
  }

  public static boolean isEmpty(final String str) {
    return str == null || str.isEmpty();
  }

  public static String toStringOrNull(final Object object) {
    return toStringOrDefault(object, null);
  }

  public static String toStringOrDefault(final Object object, final String defaultString) {
    return object == null ? defaultString : object.toString();
  }

  public static String[] toStringArrayOrNull(final List<String> inputList) {
    if (inputList == null || inputList.isEmpty()) {
      return null;
    }
    return inputList.toArray(new String[0]);
  }

  public static String trimWhitespace(final String str) {
    return (str == null) ? null : str.strip();
  }

  public static OffsetDateTime toZonedOffsetDateTime(final Instant timestamp) {
    return timestamp != null ? OffsetDateTime.ofInstant(timestamp, ZoneId.systemDefault()) : null;
  }

  public static OffsetDateTime toOffsetDateTime(final Instant timestamp) {
    return OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC);
  }

  public static OffsetDateTime toOffsetDateTime(final String timestamp) {
    return isEmpty(timestamp)
        ? null
        : toOffsetDateTime(timestamp, DateTimeFormatter.ISO_ZONED_DATE_TIME);
  }

  public static OffsetDateTime toOffsetDateTime(
      final String timestamp, final DateTimeFormatter dateTimeFormatter) {
    try {
      final ZonedDateTime zonedDateTime = ZonedDateTime.parse(timestamp, dateTimeFormatter);
      return OffsetDateTime.ofInstant(zonedDateTime.toInstant(), ZoneId.systemDefault());
    } catch (final DateTimeParseException e) {
      LOGGER.error(String.format("Cannot parse date from %s - %s", timestamp, e.getMessage()), e);
    }

    return null;
  }

  public static BatchOperationType map(final OperationType operationType) {
    return BatchOperationType.valueOf(operationType.name());
  }

  public static OperationType map(final BatchOperationType operationType) {
    return OperationType.valueOf(operationType.name());
  }
}
