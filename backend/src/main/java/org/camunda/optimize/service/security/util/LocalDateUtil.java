/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class LocalDateUtil {

  private volatile static OffsetDateTime CURRENT_TIME = null;

  public static void setCurrentTime(OffsetDateTime currentTime) {
    LocalDateUtil.CURRENT_TIME = currentTime;
  }

  public static void reset() {
    LocalDateUtil.CURRENT_TIME = null;
  }

  public static OffsetDateTime getCurrentDateTime() {
    if (CURRENT_TIME != null) {
      return CURRENT_TIME;
    }
    return OffsetDateTime.now();
  }

  public static LocalDateTime getCurrentLocalDateTime() {
    return getCurrentDateTime().toLocalDateTime();
  }

}
