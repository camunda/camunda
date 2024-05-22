/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RoundingUtil {

  public static Double roundDownToNearestPowerOfTen(final Double numberToRound) {
    if (numberToRound >= 0) {
      return Math.pow(10.0D, roundDown(numberToRound));
    } else {
      // round up if numberToRound is negative and apply sign again after rounding
      return Math.pow(10.0D, roundUp(Math.abs(numberToRound))) * -1;
    }
  }

  public static Double roundUpToNearestPowerOfTen(final Double numberToRound) {
    if (numberToRound >= 0) {
      return Math.pow(10.0D, roundUp(numberToRound));
    } else {
      // round down if numberToRound is negative and apply sign again after rounding
      return Math.pow(10.0D, roundDown(Math.abs(numberToRound))) * -1;
    }
  }

  private static long round(final Double numberToRound) {
    return Math.round(Math.log10(numberToRound));
  }

  private static double roundUp(final Double numberToRound) {
    return Math.ceil(Math.log10(numberToRound));
  }

  private static double roundDown(final Double numberToRound) {
    return Math.floor(Math.log10(numberToRound));
  }
}
