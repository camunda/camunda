/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;

import java.util.UUID;
import java.util.regex.Pattern;

public class IdGenerator {
  public static final Pattern ID_PATTERN = Pattern.compile(
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
  );

  public static String getNextId() {
    UUID randomUUID = UUID.randomUUID();
    return randomUUID.toString();
  }

  public static boolean isValidId(final ReportLocationDto reportLocationDto) {
    return IdGenerator.ID_PATTERN.matcher(reportLocationDto.getId()).matches();
  }
}
