/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.pubsub.publish;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/** Zero-allocation utility for reading fields from raw byte arrays. */
public final class ByteUtil {

  private static final VarHandle INT_HANDLE =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

  private ByteUtil() {}

  public static int readInt(final byte[] bytes, final int offset) {
    return (int) INT_HANDLE.get(bytes, offset);
  }
}
