/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

public class BackoffIdleStrategy {

  private static final int NOT_IDLE = 0;
  private static final int IDLE = 1;

  private final long baseIdleTime;
  private final float idleIncreaseFactor;
  private final long maxIdleTime;
  private final int maxIdles;

  private int state = NOT_IDLE;
  private int idles;

  public BackoffIdleStrategy(final long baseIdleTime,
      final float idleIncreaseFactor,
      final long maxIdleTime) {
    this.baseIdleTime = baseIdleTime;
    this.idleIncreaseFactor = idleIncreaseFactor;
    this.maxIdleTime = maxIdleTime;
    this.maxIdles = ((int) log(idleIncreaseFactor, maxIdleTime/baseIdleTime)) + 1;
  }

  private double log(double base, double value) {
    return Math.log10(value) / Math.log10(base);
  }

  public void idle() {
    switch (state) {
      case NOT_IDLE:
        state = IDLE;
        idles = 1;
        break;

      case IDLE:
        idles++;
        idles = Math.min(idles, maxIdles);
    }
  }

  public void reset() {
    idles = 0;
    state = NOT_IDLE;
  }

  public long idleTime() {
    final long idleTime;

    switch (state) {
      case NOT_IDLE:
        idleTime = 0;
        break;

      default:
        final var t = (long) (baseIdleTime * Math.pow(idleIncreaseFactor, idles - 1));
        idleTime = Math.min(maxIdleTime, t);
    }

    return idleTime;
  }

}
