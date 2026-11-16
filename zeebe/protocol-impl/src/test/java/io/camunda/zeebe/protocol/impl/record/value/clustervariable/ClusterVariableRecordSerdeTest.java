/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link ClusterVariableRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class ClusterVariableRecordSerdeTest {

  private static final String NAME = "a cluster variable name that is longer than forty bytes";
  private static final String TENANT = "a tenant id that is longer than forty bytes of utf8";
  private static final String SECRET_REFERENCE =
      "a secret reference that is longer than forty bytes";
  // the msgpack map {"a": 1}
  private static final DirectBuffer METADATA =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 'a', 0x01});
  private static final DirectBuffer VALUE =
      utf8("\"a json value that is longer than forty bytes\"");

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new ClusterVariableRecordV1();
    final var current = new ClusterVariableRecord();
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
    // given only the property without a default is set, everything else stays default
    final var v1 = new ClusterVariableRecordV1();
    final var current = new ClusterVariableRecord();
    v1.setName(NAME);
    current.setName(NAME);

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
    final var v1 = new ClusterVariableRecordV1();
    final var current = new ClusterVariableRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new ClusterVariableRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ClusterVariableRecord();
    current
        .setName("dirty")
        .setValue(utf8("dirty"))
        .setTenantId("dirty")
        .setKind(ClusterVariableKind.SECRET_REFERENCE)
        .setGlobalScope()
        .setMetadata(new UnsafeBuffer(new byte[] {(byte) 0x80}))
        .addSecretReference("dirty", "dirty", "dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getName()).isEqualTo(NAME);
    assertThat(current.getValueBuffer()).isEqualTo(VALUE);
    assertThat(current.getTenantId()).isEqualTo(TENANT);
    assertThat(current.getKind()).isEqualTo(ClusterVariableKind.SECRET_REFERENCE);
    assertThat(current.getScope()).isEqualTo(ClusterVariableScope.TENANT);
    assertThat(current.getMetadataBuffer()).isEqualTo(METADATA);
    assertThat(current.getSecretReferences()).hasSize(1);
    assertThat(current.getSecretReferences().get(0).getSecretReference())
        .isEqualTo(SECRET_REFERENCE);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the property without a default
    final var v1 = new ClusterVariableRecordV1();
    v1.setName(NAME);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new ClusterVariableRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then re-encoding gives the bytes of the defaults, so no dirtied property survived
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(bytes, length));
    assertThat(current.hasSecretReferences()).isFalse();
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new ClusterVariableRecordV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new ClusterVariableRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new ClusterVariableRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new ClusterVariableRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getName()).isEqualTo(NAME);
    assertThat(v1.getValueBuffer()).isEqualTo(VALUE);
    assertThat(v1.getTenantId()).isEqualTo(TENANT);
    assertThat(v1.getKind()).isEqualTo(ClusterVariableKind.SECRET_REFERENCE);
    assertThat(v1.getScope()).isEqualTo(ClusterVariableScope.TENANT);
    assertThat(v1.getMetadataBuffer()).isEqualTo(METADATA);
    assertThat(v1.getSecretReferences()).hasSize(1);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new ClusterVariableRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has seven entries, plus one unknown: 0x87 becomes 0x88
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new ClusterVariableRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setName("other");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new ClusterVariableRecordV1();
    final var current = new ClusterVariableRecord();
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

  private static void setAllProperties(final ClusterVariableRecordV1 record) {
    record
        .setName(NAME)
        .setValue(VALUE)
        .setScope(ClusterVariableScope.TENANT)
        .setTenantId(TENANT)
        .setMetadata(METADATA)
        .setKind(ClusterVariableKind.SECRET_REFERENCE)
        .addSecretReference("store", SECRET_REFERENCE, "/path");
  }

  private static void setAllProperties(final ClusterVariableRecord record) {
    record
        .setName(NAME)
        .setValue(VALUE)
        .setScope(ClusterVariableScope.TENANT)
        .setTenantId(TENANT)
        .setMetadata(METADATA)
        .setKind(ClusterVariableKind.SECRET_REFERENCE)
        .addSecretReference("store", SECRET_REFERENCE, "/path");
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
