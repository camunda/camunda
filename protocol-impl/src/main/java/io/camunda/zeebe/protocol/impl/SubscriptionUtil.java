/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;

import org.agrona.DirectBuffer;

public final class SubscriptionUtil {

  /**
   * Get the hash code of the subscription based on the given correlation key.
   *
   * @param correlationKey the correlation key
   * @return the hash code of the subscription
   */
  static int getSubscriptionHashCode(final DirectBuffer correlationKey) {
    // is equal to java.lang.String#hashCode
    int hashCode = 0;

    for (int i = 0, length = correlationKey.capacity(); i < length; i++) {
      hashCode = 31 * hashCode + correlationKey.getByte(i);
    }
    return hashCode;
  }

  /**
   * Get the partition id for message subscription based on the given correlation key.
   *
   * @param correlationKey the correlation key
   * @param partitionCount the number of partitions
   * @return the partition id for the subscription
   */
  public static int getSubscriptionPartitionId(
      final DirectBuffer correlationKey, final int partitionCount) {
    final int hashCode = getSubscriptionHashCode(correlationKey);
    // partition ids range from START_PARTITION_ID .. START_PARTITION_ID + partitionCount
    return Math.abs(hashCode % partitionCount) + START_PARTITION_ID;
  }
}
