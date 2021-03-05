/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl;

import java.nio.ByteOrder;

public final class ZeebeDbConstants {

  /**
   * The byte order is used to write primitive data types into rocks db key or value buffers.
   *
   * <p>Be aware that {@link ByteOrder.LITTLE_ENDIAN} will reverse the ordering. If the keys start
   * with an long, like an timestamp, and the implementation depends on the correct ordering, then
   * this could be a problem.
   *
   * <p>Example: Say we have `1` and `256` as keys (type short), in little endian this means 1 =
   * 0000 0001 0000 0000 and 256 = 0000 0000 0000 0001. This means that 256 will be sorted before 1
   * in Rocks DB, because the first byte is smaller.
   *
   * <p>We use {@link ByteOrder.BIG_ENDIAN} for the ascending ordering.
   */
  public static final ByteOrder ZB_DB_BYTE_ORDER = ByteOrder.BIG_ENDIAN;
}
