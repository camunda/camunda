/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import static io.camunda.zeebe.logstreams.impl.serializer.TestUtils.readString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.logstreams.impl.log.Sequencer.SequencedBatch;
import io.camunda.zeebe.logstreams.util.MutableLogAppendEntry;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class SequencedBatchSerializerTest {
  @Test
  void serializedBatchIsReadableAsLoggedEvents() {
    // given
    final var serializer = new SequencedBatchSerializer();
    final var batch =
        new SequencedBatch(
            1,
            -1,
            List.of(
                new MutableLogAppendEntry()
                    .key(1)
                    .recordMetadata(wrapString("metadata1"))
                    .recordValue(wrapString("value1")),
                new MutableLogAppendEntry()
                    .key(2)
                    .recordMetadata(wrapString("metadata2"))
                    .recordValue(wrapString("value2"))));

    // when
    final var serialized = serializer.serializeBatch(batch);

    // then
    final var firstEvent = new LoggedEventImpl();
    firstEvent.wrap(new UnsafeBuffer(serialized), 0);
    assertThat(firstEvent.getKey()).isEqualTo(1);
    assertThat(firstEvent.getPosition()).isEqualTo(1);
    assertThat(firstEvent.getSourceEventPosition()).isEqualTo(-1);
    assertThat(readString(firstEvent::readMetadata)).isEqualTo("metadata1");
    assertThat(readString(firstEvent::readValue)).isEqualTo("value1");

    final var secondEvent = new LoggedEventImpl();
    secondEvent.wrap(new UnsafeBuffer(serialized), firstEvent.getLength());
    assertThat(secondEvent.getKey()).isEqualTo(2);
    assertThat(secondEvent.getPosition()).isEqualTo(2);
    assertThat(secondEvent.getSourceEventPosition()).isEqualTo(-1);
    assertThat(readString(secondEvent::readMetadata)).isEqualTo("metadata2");
    assertThat(readString(secondEvent::readValue)).isEqualTo("value2");
  }
}
