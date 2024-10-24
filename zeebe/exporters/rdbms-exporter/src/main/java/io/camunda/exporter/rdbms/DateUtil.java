/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class DateUtil {

  private DateUtil() {}

  public static OffsetDateTime toOffsetDateTime(final Instant timestamp) {
    return OffsetDateTime.ofInstant(timestamp, ZoneOffset.UTC);
  }
}
