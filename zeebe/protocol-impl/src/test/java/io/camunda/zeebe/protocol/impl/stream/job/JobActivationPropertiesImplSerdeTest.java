/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.stream.job.legacy.JobActivationPropertiesImpl} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class JobActivationPropertiesImplSerdeTest {

  private static final String LONG_VARIABLE =
      "a-variable-name-that-is-longer-than-thirty-two-bytes";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.stream.job.legacy.JobActivationPropertiesImpl();
    final var current = new JobActivationPropertiesImpl();
    v1.setWorker(utf8("worker-1"), 0, 8)
        .setTimeout(5000L)
        .setFetchVariables(variables())
        .setTenantIds(List.of("tenant-a", "tenant-b"))
        .setTenantFilter(TenantFilter.ASSIGNED)
        .setClaims(Map.of("sub", "user", "level", 3))
        .setWithLease(true);
    current
        .setWorker(utf8("worker-1"), 0, 8)
        .setTimeout(5000L)
        .setFetchVariables(variables())
        .setTenantIds(List.of("tenant-a", "tenant-b"))
        .setTenantFilter(TenantFilter.ASSIGNED)
        .setClaims(Map.of("sub", "user", "level", 3))
        .setWithLease(true);

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
    // given every property has a default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.stream.job.legacy.JobActivationPropertiesImpl();
    final var current = new JobActivationPropertiesImpl();

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
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.stream.job.legacy.JobActivationPropertiesImpl();
    v1.setWorker(utf8("worker-1"), 0, 8)
        .setTimeout(5000L)
        .setFetchVariables(variables())
        .setTenantIds(List.of("tenant-a", "tenant-b"))
        .setTenantFilter(TenantFilter.ASSIGNED)
        .setClaims(Map.of("sub", "user"))
        .setWithLease(true);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new JobActivationPropertiesImpl();
    current
        .setWorker(utf8("dirty"), 0, 5)
        .setTimeout(1L)
        .setFetchVariables(Set.of(new StringValue("dirty")))
        .setTenantIds(List.of("dirty"))
        .setTenantFilter(TenantFilter.ASSIGNED)
        .setClaims(Map.of("dirty", true))
        .setWithLease(true);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(BufferUtil.bufferAsString(current.worker())).isEqualTo("worker-1");
    assertThat(current.timeout()).isEqualTo(5000L);
    assertThat(current.fetchVariables().stream().map(BufferUtil::bufferAsString))
        .containsExactly("x", LONG_VARIABLE);
    assertThat(current.tenantIds()).containsExactly("tenant-a", "tenant-b");
    assertThat(current.tenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    assertThat(current.claims()).isEqualTo(Map.of("sub", "user"));
    assertThat(current.withLease()).isTrue();
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var defaults =
        new io.camunda.zeebe.protocol.impl.stream.job.legacy.JobActivationPropertiesImpl();
    final var bytes = new ExpandableArrayBuffer();
    final int length = defaults.write(bytes, 0);
    final var current = new JobActivationPropertiesImpl();
    current
        .setWorker(utf8("dirty"), 0, 5)
        .setTimeout(1L)
        .setFetchVariables(Set.of(new StringValue("dirty")))
        .setTenantIds(List.of("dirty"))
        .setTenantFilter(TenantFilter.ASSIGNED)
        .setClaims(Map.of("dirty", true))
        .setWithLease(true);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.worker()).isEqualTo(defaults.worker());
    assertThat(current.timeout()).isEqualTo(defaults.timeout()).isEqualTo(-1L);
    assertThat(current.fetchVariables()).isEmpty();
    assertThat(current.tenantIds()).isEmpty();
    assertThat(current.tenantFilter()).isEqualTo(defaults.tenantFilter());
    assertThat(current.claims()).isEqualTo(defaults.claims());
    assertThat(current.withLease()).isFalse();
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new JobActivationPropertiesImpl();
    current
        .setWorker(utf8("worker-1"), 0, 8)
        .setTimeout(5000L)
        .setFetchVariables(variables())
        .setTenantIds(List.of("tenant-a", "tenant-b"))
        .setTenantFilter(TenantFilter.ASSIGNED)
        .setClaims(Map.of("sub", "user"))
        .setWithLease(true);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.stream.job.legacy.JobActivationPropertiesImpl();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(BufferUtil.bufferAsString(v1.worker())).isEqualTo("worker-1");
    assertThat(v1.timeout()).isEqualTo(5000L);
    assertThat(v1.fetchVariables().stream().map(BufferUtil::bufferAsString))
        .containsExactly("x", LONG_VARIABLE);
    assertThat(v1.tenantIds()).containsExactly("tenant-a", "tenant-b");
    assertThat(v1.tenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    assertThat(v1.claims()).isEqualTo(Map.of("sub", "user"));
    assertThat(v1.withLease()).isTrue();
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new JobActivationPropertiesImpl();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new JobActivationPropertiesImpl();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setTimeout(42L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static Set<StringValue> variables() {
    return new LinkedHashSet<>(List.of(new StringValue("x"), new StringValue(LONG_VARIABLE)));
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
