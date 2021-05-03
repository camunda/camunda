/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.protocol.impl.SubscriptionUtil.getSubscriptionHashCode;
import static io.zeebe.protocol.impl.SubscriptionUtil.getSubscriptionPartitionId;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

public final class SubscriptionUtilTest {

  @Test
  public void shouldGetSubscriptionHashCode() {
    assertThat(getSubscriptionHashCode(wrapString("a"))).isEqualTo(97);
    assertThat(getSubscriptionHashCode(wrapString("b"))).isEqualTo(98);
    assertThat(getSubscriptionHashCode(wrapString("c"))).isEqualTo(99);
    assertThat(getSubscriptionHashCode(wrapString("foobar"))).isEqualTo(-1268878963);
  }

  @Test
  public void shouldGetZeroSubscriptionHashCodeIfEmpty() {
    assertThat(getSubscriptionHashCode(new UnsafeBuffer())).isEqualTo(0);
  }

  @Test
  public void shouldGetPartitionIdForCorrelationKey() {
    assertThat(getSubscriptionPartitionId(wrapString("a"), 10)).isEqualTo(7 + START_PARTITION_ID);
    assertThat(getSubscriptionPartitionId(wrapString("b"), 3)).isEqualTo(2 + START_PARTITION_ID);
    assertThat(getSubscriptionPartitionId(wrapString("c"), 11)).isEqualTo(0 + START_PARTITION_ID);
    assertThat(getSubscriptionPartitionId(wrapString("foobar"), 100))
        .isEqualTo(63 + START_PARTITION_ID);
  }
}
