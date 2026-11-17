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
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.probe.legacy.ProbeRecord} is a frozen
 * copy of the property based implementation the generated codec replaced.
 */
final class ProbeRecordSerdeTest {

  // the msgpack map {"a": 1}
  private static final DirectBuffer VARIABLES =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 0xa1, 'a', 0x01});
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";
  private static final String PROBE_ID = "validate-configuration/io.camunda:aws-credential:1";
  private static final String EXPRESSION = "={accessKey: camunda.secrets.awsKey}";
  private static final String MESSAGE = "a message that is longer than forty bytes of utf8 text";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.probe.legacy.ProbeRecord();
    final var current = new ProbeRecord();
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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.probe.legacy.ProbeRecord();
    final var current = new ProbeRecord();

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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.probe.legacy.ProbeRecord();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProbeRecord();
    current
        .setProbeId("dirty")
        .setTenantId("dirty")
        .setInputExpression("dirty")
        .setInput(new UnsafeBuffer(new byte[] {(byte) 0x80}))
        .addSecretReference("dirty", "dirty", "dirty")
        .setRequestTimeout(7L)
        .setDeadline(7L)
        .setWorker("dirty")
        .setOutcome(ProbeOutcome.OK)
        .setData(new UnsafeBuffer(new byte[] {(byte) 0x80}))
        .setMessage("dirty")
        .setEndReason(ProbeEndReason.NO_WORKER);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getProbeId()).isEqualTo(PROBE_ID);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getInputExpression()).isEqualTo(EXPRESSION);
    assertThat(current.getInputBuffer()).isEqualTo(VARIABLES);
    assertThat(current.getSecretReferences()).hasSize(2);
    assertThat(current.getSecretReferences().get(1).getPath()).isEqualTo("/token");
    assertThat(current.getRequestTimeout()).isEqualTo(5000L);
    assertThat(current.getDeadline()).isEqualTo(123L);
    assertThat(current.getWorker()).isEqualTo("connectors");
    assertThat(current.getOutcome()).isEqualTo(ProbeOutcome.ERROR);
    assertThat(current.getDataBuffer()).isEqualTo(VARIABLES);
    assertThat(current.getMessage()).isEqualTo(MESSAGE);
    assertThat(current.getEndReason()).isEqualTo(ProbeEndReason.WORKER_DID_NOT_ANSWER);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with every property at its default
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.probe.legacy.ProbeRecord();
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ProbeRecord();
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
    final var current = new ProbeRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.probe.legacy.ProbeRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getProbeId()).isEqualTo(PROBE_ID);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getInputExpression()).isEqualTo(EXPRESSION);
    assertThat(v1.getInputBuffer()).isEqualTo(VARIABLES);
    assertThat(v1.getSecretReferences()).hasSize(2);
    assertThat(v1.getSecretReferences().get(1).getPath()).isEqualTo("/token");
    assertThat(v1.getRequestTimeout()).isEqualTo(5000L);
    assertThat(v1.getDeadline()).isEqualTo(123L);
    assertThat(v1.getWorker()).isEqualTo("connectors");
    assertThat(v1.getOutcome()).isEqualTo(ProbeOutcome.ERROR);
    assertThat(v1.getDataBuffer()).isEqualTo(VARIABLES);
    assertThat(v1.getMessage()).isEqualTo(MESSAGE);
    assertThat(v1.getEndReason()).isEqualTo(ProbeEndReason.WORKER_DID_NOT_ANSWER);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ProbeRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has 12 entries, plus one unknown: 0x8c becomes 0x8d
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ProbeRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDeadline(9L);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.probe.legacy.ProbeRecord();
    final var current = new ProbeRecord();
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

  private static void setAllProperties(
      final io.camunda.zeebe.protocol.impl.record.value.probe.legacy.ProbeRecord record) {
    record
        .setProbeId(PROBE_ID)
        .setTenantId(TENANT)
        .setInputExpression(EXPRESSION)
        .setInput(VARIABLES)
        .addSecretReference("default", "awsKey", "/accessKey")
        .addSecretReference("other", "slackKey", "/token")
        .setRequestTimeout(5000L)
        .setDeadline(123L)
        .setWorker("connectors")
        .setOutcome(ProbeOutcome.ERROR)
        .setData(VARIABLES)
        .setMessage(MESSAGE)
        .setEndReason(ProbeEndReason.WORKER_DID_NOT_ANSWER);
  }

  private static void setAllProperties(final ProbeRecord record) {
    record
        .setProbeId(PROBE_ID)
        .setTenantId(TENANT)
        .setInputExpression(EXPRESSION)
        .setInput(VARIABLES)
        .addSecretReference("default", "awsKey", "/accessKey")
        .addSecretReference("other", "slackKey", "/token")
        .setRequestTimeout(5000L)
        .setDeadline(123L)
        .setWorker("connectors")
        .setOutcome(ProbeOutcome.ERROR)
        .setData(VARIABLES)
        .setMessage(MESSAGE)
        .setEndReason(ProbeEndReason.WORKER_DID_NOT_ANSWER);
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
