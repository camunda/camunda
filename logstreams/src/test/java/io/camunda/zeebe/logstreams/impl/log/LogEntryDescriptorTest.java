/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class LogEntryDescriptorTest {

  @Test
  void shouldNotSkipProcessingByDefault() {
    // given
    final var buffer = new UnsafeBuffer(new byte[128]);

    // when
    final boolean skip = LogEntryDescriptor.shouldSkipProcessing(buffer, 0);

    // then
    Assertions.assertThat(skip).isFalse();
  }

  @Test
  void canSetSkipProcessingFlag() {
    // given
    final var buffer = new UnsafeBuffer(new byte[128]);

    // when
    LogEntryDescriptor.skipProcessing(buffer, 0);

    // then
    Assertions.assertThat(LogEntryDescriptor.shouldSkipProcessing(buffer, 0)).isTrue();
  }
}
