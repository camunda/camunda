/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.dispatcher.impl;

/** Utility for composing the position */
public class PositionUtil {

  public static long position(int partitionId, int partitionOffset) {
    return ((long) partitionId) << 32 | partitionOffset & 0xFFFFFFFFL;
  }

  public static int partitionId(long position) {
    return (int) (position >> 32);
  }

  public static int partitionOffset(long position) {
    return (int) (position & 0xFFFFFFFFL);
  }
}
