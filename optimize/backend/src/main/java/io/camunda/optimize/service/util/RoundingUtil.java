/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

public class RoundingUtil {

  private RoundingUtil() {}

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
