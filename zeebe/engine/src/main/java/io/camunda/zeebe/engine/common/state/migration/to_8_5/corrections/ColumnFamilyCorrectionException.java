/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.migration.to_8_5.corrections;

import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class ColumnFamilyCorrectionException extends RuntimeException {
  public ColumnFamilyCorrectionException(
      final String reason,
      final DbBytes key,
      final DbBytes value,
      final ZbColumnFamilies columnFamily) {
    super(formatMessage(reason, key, value, columnFamily));
  }

  public ColumnFamilyCorrectionException(
      final String reason,
      final DbBytes key,
      final DbBytes value,
      final ZbColumnFamilies columnFamily,
      final Throwable cause) {
    super(formatMessage(reason, key, value, columnFamily), cause);
  }

  private static String formatMessage(
      final String reason,
      final DbBytes key,
      final DbBytes value,
      final ZbColumnFamilies columnFamily) {
    return String.format(
        "Failed to correct prefix of column family [%d] %s, due to %s - key[%s], value[%s]",
        columnFamily.ordinal(),
        columnFamily.name(),
        reason,
        BufferUtil.bufferAsHexString(key.getDirectBuffer()),
        BufferUtil.bufferAsHexString(value.getDirectBuffer()));
  }
}
