/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CronNormalizerUtil {
  public static String normalizeToSixParts(String cronTrigger) {
    String[] cronParts = cronTrigger.split(" ");
    if (cronParts.length < 6) {
      return "0 " + cronTrigger;
    } else {
      return cronTrigger;
    }
  }
}
