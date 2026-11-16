/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link ProbeBatchRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class ProbeBatchRecordSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ProbeBatchRecordV1();
    final var current = new ProbeBatchRecord();
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
    // given
    final var v1 = new ProbeBatchRecordV1();
    final var current = new ProbeBatchRecord();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new ProbeBatchRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProbeBatchRecord();
    current
        .setProbeIds(List.of("dirty"))
        .setWorker("dirty")
        .setMaxProbesToActivate(7)
        .addProbe(9L, new ProbeRecord().setProbeId("dirty"))
        .setTruncated(false)
        .setTenantIds(List.of("dirty"))
        .setTenantFilter(TenantFilter.PROVIDED);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProbeIds()).containsExactly("probe-1", "probe-2");
    assertThat(current.getWorker()).isEqualTo("connectors");
    assertThat(current.getMaxProbesToActivate()).isEqualTo(5);
    assertThat(current.getProbeKeys()).containsExactly(1L, 2L);
    assertThat(current.getProbes())
        .extracting(ProbeRecordValue::getProbeId)
        .containsExactly("probe-1", "probe-2");
    assertThat(current.isTruncated()).isTrue();
    assertThat(current.getTenantIds()).containsExactly("tenant-1", "tenant-2");
    assertThat(current.getTenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new ProbeBatchRecordV1();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProbeBatchRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ProbeBatchRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ProbeBatchRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getProbeIds()).containsExactly("probe-1", "probe-2");
    assertThat(v1.getWorker()).isEqualTo("connectors");
    assertThat(v1.getMaxProbesToActivate()).isEqualTo(5);
    assertThat(v1.getProbeKeys()).containsExactly(1L, 2L);
    assertThat(v1.getProbes())
        .extracting(ProbeRecordValue::getProbeId)
        .containsExactly("probe-1", "probe-2");
    assertThat(v1.isTruncated()).isTrue();
    assertThat(v1.getTenantIds()).containsExactly("tenant-1", "tenant-2");
    assertThat(v1.getTenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProbeBatchRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 8 entries, plus one unknown: 0x88 becomes 0x89
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProbeBatchRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setMaxProbesToActivate(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new ProbeBatchRecordV1();
    final var current = new ProbeBatchRecord();
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

  private static void setAllProperties(final ProbeBatchRecordV1 record) {
    record
        .setProbeIds(List.of("probe-1", "probe-2"))
        .setWorker("connectors")
        .setMaxProbesToActivate(5)
        .addProbe(1L, new ProbeRecordV1().setProbeId("probe-1").setWorker("connectors"))
        .addProbe(2L, new ProbeRecordV1().setProbeId("probe-2").setWorker("connectors"))
        .setTruncated(true)
        .setTenantIds(List.of("tenant-1", "tenant-2"))
        .setTenantFilter(TenantFilter.ASSIGNED);
  }

  private static void setAllProperties(final ProbeBatchRecord record) {
    record
        .setProbeIds(List.of("probe-1", "probe-2"))
        .setWorker("connectors")
        .setMaxProbesToActivate(5)
        .addProbe(1L, new ProbeRecord().setProbeId("probe-1").setWorker("connectors"))
        .addProbe(2L, new ProbeRecord().setProbeId("probe-2").setWorker("connectors"))
        .setTruncated(true)
        .setTenantIds(List.of("tenant-1", "tenant-2"))
        .setTenantFilter(TenantFilter.ASSIGNED);
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
