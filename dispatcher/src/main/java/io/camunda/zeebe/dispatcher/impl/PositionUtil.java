/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.dispatcher.impl;

/** Utility for composing the position */
public final class PositionUtil {

  public static long position(final int partitionId, final int partitionOffset) {
    return ((long) partitionId) << 32 | partitionOffset & 0xFFFFFFFFL;
  }

  public static int partitionId(final long position) {
    return (int) (position >> 32);
  }

  public static int partitionOffset(final long position) {
    return (int) (position & 0xFFFFFFFFL);
  }
}
