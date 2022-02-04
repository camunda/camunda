/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.dispatcher.DispatcherBuilder;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.Test;

final class LogStreamBatchWriterImplTest {
  @Test
  void should() {
    // given
    final var dispatcher = new DispatcherBuilder("dispatcher").build();
    final var writer = new LogStreamBatchWriterImpl(1, dispatcher);
    final var value = BufferUtil.wrapString("foo");
    final var metadata = BufferUtil.wrapString("bar");
    final var expectedLength = writer.getBatchFramedLength(value.capacity() + metadata.capacity());

    // when
    writer.event().value(value).metadata(metadata).done().tryWrite();
  }
}
