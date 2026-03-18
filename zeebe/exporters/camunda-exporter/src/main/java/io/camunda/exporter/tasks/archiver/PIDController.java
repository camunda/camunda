/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import java.time.Clock;

public class PIDController {
  private final Clock clock;
  private final double kp; // proportional gain
  private final double ki; // integral gain
  private final double kd; // derivative gain
  private double target; // desired value/set point
  private State state;

  public PIDController(final double kp, final double ki, final double kd) {
    this(Clock.systemUTC(), kp, ki, kd);
  }

  public PIDController(final Clock clock, final double kp, final double ki, final double kd) {
    this.clock = clock;
    this.kp = kp;
    this.ki = ki;
    this.kd = kd;
  }

  public void setTarget(final double target) {
    this.target = target;
    state = null;
  }

  public double update(final double actual) {
    final long currentTime = clock.millis();
    final double error = target - actual;

    System.out.println("error: " + error);

    double integral = 0.0;
    double derivative = 0.0;

    if (state != null) {
      final double dtSecs = (currentTime - state.lastUpdateTime()) / 1000.0;
      integral = state.integral() + error * dtSecs;
      derivative = (error - state.previousError()) / dtSecs;
    }

    state = new State(currentTime, integral, error);

    return kp * error + ki * integral + kd * derivative;
  }

  record State(long lastUpdateTime, double integral, double previousError) {}
}
