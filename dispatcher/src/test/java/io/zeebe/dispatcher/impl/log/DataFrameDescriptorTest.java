/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.dispatcher.impl.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class DataFrameDescriptorTest {

  @Test
  public void shouldTestFlags() {
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b0010_0000)).isTrue();
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b1111_1111)).isTrue();
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b1010_0000)).isTrue();
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b0000_0000)).isFalse();
    assertThat(DataFrameDescriptor.flagFailed((byte) 0b1000_0000)).isFalse();

    assertThat(DataFrameDescriptor.flagBatchBegin((byte) 0b1000_0000)).isTrue();
    assertThat(DataFrameDescriptor.flagBatchBegin((byte) 0b1111_1111)).isTrue();
    assertThat(DataFrameDescriptor.flagBatchBegin((byte) 0b1010_0000)).isTrue();

    assertThat(DataFrameDescriptor.flagBatchEnd((byte) 0b0100_0000)).isTrue();
    assertThat(DataFrameDescriptor.flagBatchEnd((byte) 0b1111_1111)).isTrue();
    assertThat(DataFrameDescriptor.flagBatchEnd((byte) 0b1100_0000)).isTrue();
  }

  @Test
  public void shouldEnableFlags() {
    assertThat(DataFrameDescriptor.enableFlagFailed((byte) 0b0000_0000))
        .isEqualTo((byte) 0b0010_0000);
    assertThat(DataFrameDescriptor.enableFlagFailed((byte) 0b0010_0000))
        .isEqualTo((byte) 0b0010_0000);
    assertThat(DataFrameDescriptor.enableFlagFailed((byte) 0b1101_1111))
        .isEqualTo((byte) 0b1111_1111);

    assertThat(DataFrameDescriptor.enableFlagBatchBegin((byte) 0b0000_0000))
        .isEqualTo((byte) 0b1000_0000);
    assertThat(DataFrameDescriptor.enableFlagBatchBegin((byte) 0b1000_0000))
        .isEqualTo((byte) 0b1000_0000);
    assertThat(DataFrameDescriptor.enableFlagBatchBegin((byte) 0b0111_1111))
        .isEqualTo((byte) 0b1111_1111);

    assertThat(DataFrameDescriptor.enableFlagBatchEnd((byte) 0b0000_0000))
        .isEqualTo((byte) 0b0100_0000);
    assertThat(DataFrameDescriptor.enableFlagBatchEnd((byte) 0b0100_0000))
        .isEqualTo((byte) 0b0100_0000);
    assertThat(DataFrameDescriptor.enableFlagBatchEnd((byte) 0b1011_1111))
        .isEqualTo((byte) 0b1111_1111);
  }
}
