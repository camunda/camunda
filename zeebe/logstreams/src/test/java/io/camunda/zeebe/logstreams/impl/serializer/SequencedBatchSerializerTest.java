/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import static io.camunda.zeebe.logstreams.util.TestEntry.TestEntryAssert.assertThatEntry;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.logstreams.impl.log.SequencedBatch;
import io.camunda.zeebe.logstreams.util.TestEntry;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class SequencedBatchSerializerTest {
  @Test
  void serializedBatchIsReadableAsLoggedEvents() {
    // given
    final var entries = List.of(TestEntry.ofKey(1), TestEntry.ofKey(2));
    final var batch = new SequencedBatch(0, 1, -1, entries);

    // when
    final var serialized = SequencedBatchSerializer.serializeBatch(batch);

    // then
    final var firstEvent = new LoggedEventImpl();
    firstEvent.wrap(new UnsafeBuffer(serialized), 0);
    assertThatEntry(entries.get(0)).matchesLoggedEvent(firstEvent);
    assertThat(firstEvent.getPosition()).isEqualTo(1);
    assertThat(firstEvent.getSourceEventPosition()).isEqualTo(-1);

    final var secondEvent = new LoggedEventImpl();
    secondEvent.wrap(new UnsafeBuffer(serialized), firstEvent.getLength());
    assertThatEntry(entries.get(1)).matchesLoggedEvent(secondEvent);
    assertThat(secondEvent.getPosition()).isEqualTo(2);
    assertThat(secondEvent.getSourceEventPosition()).isEqualTo(-1);
  }
}
