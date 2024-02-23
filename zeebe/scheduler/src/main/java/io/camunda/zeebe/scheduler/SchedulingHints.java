/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.scheduler;

public enum SchedulingHints {
  CPU_BOUND,
  IO_BOUND;

  public static SchedulingHints cpuBound() {
    return SchedulingHints.CPU_BOUND;
  }

  public static SchedulingHints ioBound() {
    return SchedulingHints.IO_BOUND;
  }
}
