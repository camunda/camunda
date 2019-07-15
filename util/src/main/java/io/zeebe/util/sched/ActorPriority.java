/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.sched;

/** Default Actor Priority Classes */
public enum ActorPriority {
  HIGH(0),

  REGULAR(1),

  LOW(2);

  private short priorityClass;

  ActorPriority(int priorityClass) {
    this.priorityClass = (short) priorityClass;
  }

  public short getPriorityClass() {
    return priorityClass;
  }
}
