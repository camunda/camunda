/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.NoopScheduledCommandCache;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StagedScheduledCommandCache;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BufferedTaskResultBuilderTest {

  @Test
  void canAppendEmptyRecordList() {
    final var cache = new NoopScheduledCommandCache();
    final var builder = new BufferedTaskResultBuilder((count, size) -> size < 1000, cache);

    assertThat(builder.canAppendRecords(List.of(), FollowUpCommandMetadata.empty())).isTrue();
  }

  @Test
  void canAppendSingleRecord() {
    final var cache = Mockito.mock(StagedScheduledCommandCache.class);
    final var builder = new BufferedTaskResultBuilder((count, size) -> size < 1000, cache);

    final var records = List.of(new ProcessInstanceCreationRecord());

    assertThat(builder.canAppendRecords(records, FollowUpCommandMetadata.empty())).isTrue();
  }

  @Test
  void canAppendRecords() {
    final var cache = Mockito.mock(StagedScheduledCommandCache.class);
    final var builder = new BufferedTaskResultBuilder((count, size) -> size < 1000, cache);

    final var records =
        List.of(
            new ProcessInstanceCreationRecord(),
            new ProcessInstanceCreationRecord(),
            new ProcessInstanceCreationRecord());

    assertThat(builder.canAppendRecords(records, FollowUpCommandMetadata.empty()))
        .isTrue(); // size should be 801
  }

  @Test
  void cannotAppendRecords() {
    final var cache = Mockito.mock(StagedScheduledCommandCache.class);
    final var builder = new BufferedTaskResultBuilder((count, size) -> size < 1000, cache);

    final var records =
        List.of(
            new ProcessInstanceCreationRecord(),
            new ProcessInstanceCreationRecord(),
            new ProcessInstanceCreationRecord(),
            new ProcessInstanceCreationRecord(),
            new ProcessInstanceCreationRecord());

    assertThat(builder.canAppendRecords(records, FollowUpCommandMetadata.empty()))
        .isFalse(); // size should be 1335
  }
}
