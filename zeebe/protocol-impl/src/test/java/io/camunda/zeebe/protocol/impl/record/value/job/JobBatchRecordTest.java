/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class JobBatchRecordTest {

  @Test
  void shouldWrapEquivalentlyToCopyFrom() {
    // given - a record with every property populated
    final JobBatchRecord record = fullyPopulated();
    final var copied = new JobBatchRecord();
    copied.copyFrom(record);

    // when
    final var wrapped = new JobBatchRecord();
    wrapped.wrap(record);

    // then - wrapping shares the source's buffers but serializes to the same bytes as copying
    assertThat(serialized(wrapped)).isEqualTo(serialized(copied));
  }

  @Test
  void shouldResetPreviousStateOnWrap() {
    // given - a reused record that already wraps another, larger batch
    final var reused = new JobBatchRecord();
    reused.wrap(fullyPopulated());
    final var smallBatch = new JobBatchRecord().setType("small-type");
    smallBatch.jobKeys().add().setValue(42L);
    smallBatch.jobs().add().setType("small-type");

    // when
    reused.wrap(smallBatch);

    // then - no state of the previous batch leaks into the reused record
    final var copied = new JobBatchRecord();
    copied.copyFrom(smallBatch);
    assertThat(serialized(reused)).isEqualTo(serialized(copied));
  }

  private static JobBatchRecord fullyPopulated() {
    final var record =
        new JobBatchRecord()
            .setType("test-type")
            .setWorker("test-worker")
            .setTimeout(1000L)
            .setMaxJobsToActivate(10)
            .setTruncated(true)
            .setWithLease(true)
            .setTenantFilter(TenantFilter.ASSIGNED)
            .setTenantIds(List.of("tenant-1", "tenant-2"));
    record.variables().add().wrap(BufferUtil.wrapString("fetchedVariable"));
    record.jobKeys().add().setValue(1L);
    record.jobKeys().add().setValue(2L);
    record
        .jobs()
        .add()
        .setType("test-type")
        .setWorker("test-worker")
        .setVariables(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack("{\"foo\":\"bar\"}")))
        .addSecretReference("store-1", "token", "/auth/token");
    record.jobs().add().setType("test-type").setRetries(3);
    return record;
  }

  private static byte[] serialized(final JobBatchRecord record) {
    final var buffer = new UnsafeBuffer(new byte[record.getLength()]);
    record.write(buffer, 0);
    return buffer.byteArray();
  }
}
