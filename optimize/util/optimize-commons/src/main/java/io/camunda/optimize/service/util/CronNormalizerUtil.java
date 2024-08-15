/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

public class CronNormalizerUtil {

  private CronNormalizerUtil() {}

  public static String normalizeToSixParts(final String cronTrigger) {
    final String[] cronParts = cronTrigger.split(" ");
    if (cronParts.length < 6) {
      return "0 " + cronTrigger;
    } else {
      return cronTrigger;
    }
  }
}
