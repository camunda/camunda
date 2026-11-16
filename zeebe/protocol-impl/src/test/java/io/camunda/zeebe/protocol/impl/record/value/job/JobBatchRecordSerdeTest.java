/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link JobBatchRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class JobBatchRecordSerdeTest {

  private static final String TYPE = "a-job-type-that-is-longer-than-thirty-two-bytes";
  private static final String WORKER = "a-worker-name-that-is-longer-than-thirty-two-bytes";

  private static void populate(final JobBatchRecordV1 record) {
    record
        .setType(TYPE)
        .setWorker(WORKER)
        .setTimeout(65_536L)
        .setMaxJobsToActivate(70_000)
        .setTruncated(true)
        .setWithLease(true)
        .setTenantFilter(TenantFilter.ASSIGNED)
        .setTenantIds(List.of("tenant-a", "tenant-b"));
    record.jobKeys().add().setValue(300L);
    record.jobKeys().add().setValue(4_294_967_296L);
    record.jobs().add().setType("job-a").setRetries(3).setWorker(WORKER);
    record.jobs().add().setType("job-b").setPriority(70_000);
    record.variables().add().wrap(utf8("foo"));
    record.variables().add().wrap(utf8("a-variable-name-longer-than-thirty-two-bytes"));
  }

  private static void populate(final JobBatchRecord record) {
    record
        .setType(TYPE)
        .setWorker(WORKER)
        .setTimeout(65_536L)
        .setMaxJobsToActivate(70_000)
        .setTruncated(true)
        .setWithLease(true)
        .setTenantFilter(TenantFilter.ASSIGNED)
        .setTenantIds(List.of("tenant-a", "tenant-b"));
    record.jobKeys().add().setValue(300L);
    record.jobKeys().add().setValue(4_294_967_296L);
    record.jobs().add().setType("job-a").setRetries(3).setWorker(WORKER);
    record.jobs().add().setType("job-b").setPriority(70_000);
    record.variables().add().wrap(utf8("foo"));
    record.variables().add().wrap(utf8("a-variable-name-longer-than-thirty-two-bytes"));
  }

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new JobBatchRecordV1();
    final var current = new JobBatchRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength).isEqualTo(current.getLength()).isEqualTo(v1.getLength());
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only the properties without a default are set, everything else stays default
    final var v1 = new JobBatchRecordV1();
    final var current = new JobBatchRecord();
    setRequiredProperties(v1);
    setRequiredProperties(current);

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldFailToWriteWhileUnsetLikeV1() {
    // given
    final var v1 = new JobBatchRecordV1();
    final var current = new JobBatchRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new JobBatchRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobBatchRecord();
    current
        .setType("dirty")
        .setWorker("dirty")
        .setTimeout(1L)
        .setMaxJobsToActivate(1)
        .setTruncated(true)
        .setWithLease(true)
        .setTenantIds(List.of("dirty"));
    current.jobKeys().add().setValue(1L);
    current.jobs().add().setType("dirty");
    current.variables().add().wrap(utf8("dirty"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertAllProperties(current);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the properties without a default
    final var v1 = new JobBatchRecordV1();
    setRequiredProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobBatchRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new JobBatchRecordV1();
    setRequiredProperties(defaults);
    assertDefaultsOf(defaults, current);
  }

  @Test
  void shouldFailToReadWithoutTheKeysLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new JobBatchRecordV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new JobBatchRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new JobBatchRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new JobBatchRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertAllProperties(v1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new JobBatchRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has eleven entries, plus one unknown: 0x8b becomes 0x8c
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new JobBatchRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setTimeout(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new JobBatchRecordV1();
    final var current = new JobBatchRecord();
    setAllProperties(v1);
    setAllProperties(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  private static void setAllProperties(final JobBatchRecordV1 record) {
    populate(record);
  }

  private static void setAllProperties(final JobBatchRecord record) {
    populate(record);
  }

  private static void setRequiredProperties(final JobBatchRecordV1 record) {
    record.setType(TYPE);
  }

  private static void setRequiredProperties(final JobBatchRecord record) {
    record.setType(TYPE);
  }

  private static void assertAllProperties(final JobBatchRecordV1 record) {
    assertThat(record.getType()).isEqualTo(TYPE);
    assertThat(record.getWorker()).isEqualTo(WORKER);
    assertThat(record.getTimeout()).isEqualTo(65_536L);
    assertThat(record.getMaxJobsToActivate()).isEqualTo(70_000);
    assertThat(record.getJobKeys()).containsExactly(300L, 4_294_967_296L);
    assertThat(record.getJobs())
        .extracting(JobRecordValue::getType)
        .containsExactly("job-a", "job-b");
    assertThat(record.getJobs().getFirst().getRetries()).isEqualTo(3);
    assertThat(record.getJobs().getLast().getPriority()).isEqualTo(70_000);
    assertThat(record.variables())
        .extracting(variable -> BufferUtil.bufferAsString(variable.getValue()))
        .containsExactly("foo", "a-variable-name-longer-than-thirty-two-bytes");
    assertThat(record.isTruncated()).isTrue();
    assertThat(record.isWithLease()).isTrue();
    assertThat(record.getTenantIds()).containsExactly("tenant-a", "tenant-b");
    assertThat(record.getTenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
  }

  private static void assertAllProperties(final JobBatchRecord record) {
    assertThat(record.getType()).isEqualTo(TYPE);
    assertThat(record.getWorker()).isEqualTo(WORKER);
    assertThat(record.getTimeout()).isEqualTo(65_536L);
    assertThat(record.getMaxJobsToActivate()).isEqualTo(70_000);
    assertThat(record.getJobKeys()).containsExactly(300L, 4_294_967_296L);
    assertThat(record.getJobs())
        .extracting(JobRecordValue::getType)
        .containsExactly("job-a", "job-b");
    assertThat(record.getJobs().getFirst().getRetries()).isEqualTo(3);
    assertThat(record.getJobs().getLast().getPriority()).isEqualTo(70_000);
    assertThat(record.variables())
        .extracting(variable -> BufferUtil.bufferAsString(variable.getValue()))
        .containsExactly("foo", "a-variable-name-longer-than-thirty-two-bytes");
    assertThat(record.isTruncated()).isTrue();
    assertThat(record.isWithLease()).isTrue();
    assertThat(record.getTenantIds()).containsExactly("tenant-a", "tenant-b");
    assertThat(record.getTenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
  }

  private static void assertDefaultsOf(
      final JobBatchRecordV1 defaults, final JobBatchRecord record) {
    assertThat(record.getType()).isEqualTo(defaults.getType());
    assertThat(record.getWorker()).isEqualTo(defaults.getWorker());
    assertThat(record.getTimeout()).isEqualTo(defaults.getTimeout());
    assertThat(record.getMaxJobsToActivate()).isEqualTo(defaults.getMaxJobsToActivate());
    assertThat(record.getJobKeys()).isEmpty();
    assertThat(record.getJobs()).isEmpty();
    assertThat(record.variables().size()).isZero();
    assertThat(record.isTruncated()).isEqualTo(defaults.isTruncated());
    assertThat(record.isWithLease()).isEqualTo(defaults.isWithLease());
    assertThat(record.getTenantIds()).isEmpty();
    assertThat(record.getTenantFilter()).isEqualTo(defaults.getTenantFilter());
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String hex(final ExpandableArrayBuffer buffer, final int length) {
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
