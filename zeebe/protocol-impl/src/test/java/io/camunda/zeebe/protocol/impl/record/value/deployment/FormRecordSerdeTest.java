/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class FormRecordSerdeTest {

  private static final String FORM_ID = "form-id-that-is-longer-than-thirty-two-bytes";
  private static final byte[] CHECKSUM = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
  private static final String RESOURCE = "{\"components\":[]}";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord();
    final var current = new FormRecord();
    populate(v1);
    populate(current);

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
    // given only what cannot be written unset, everything else stays default
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord();
    final var current = new FormRecord();
    setRequired(v1);
    setRequired(current);

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
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord();
    populate(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new FormRecord();
    current.setFormId("other");
    current.setVersion(5);
    current.setFormKey(7L);
    current.setResourceName("other.form");
    current.setTenantId("tenant-b");
    current.setDeploymentKey(9L);
    current.setVersionTag("other-tag");
    current.setResource(new UnsafeBuffer(new byte[] {9}));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getFormId()).isEqualTo(FORM_ID);
    assertThat(current.getVersion()).isEqualTo(300);
    assertThat(current.getFormKey()).isEqualTo(4_294_967_296L);
    assertThat(current.getResourceName()).isEqualTo("form.form");
    assertThat(current.getChecksum()).containsExactly(CHECKSUM);
    assertThat(current.getTenantId()).isEqualTo("tenant-a");
    assertThat(current.getDeploymentKey()).isEqualTo(4_294_967_297L);
    assertThat(current.getVersionTag()).isEqualTo("version-tag");
    assertThat(current.getResource()).isEqualTo(RESOURCE.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord();
    setRequired(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new FormRecord();
    populate(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord();
    assertThat(current.getFormId()).isEqualTo(FORM_ID);
    assertThat(current.getChecksum()).isEmpty();
    assertThat(current.getResource()).isEmpty();
    assertThat(current.getTenantId()).isEqualTo(defaults.getTenantId());
    assertThat(current.getDeploymentKey()).isEqualTo(defaults.getDeploymentKey());
    assertThat(current.getVersionTag()).isEqualTo(defaults.getVersionTag());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new FormRecord();
    populate(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getFormId()).isEqualTo(FORM_ID);
    assertThat(v1.getVersion()).isEqualTo(300);
    assertThat(v1.getFormKey()).isEqualTo(4_294_967_296L);
    assertThat(v1.getResourceName()).isEqualTo("form.form");
    assertThat(v1.getChecksum()).containsExactly(CHECKSUM);
    assertThat(v1.getDeploymentKey()).isEqualTo(4_294_967_297L);
    assertThat(v1.getVersionTag()).isEqualTo("version-tag");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new FormRecord();
    populate(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has nine entries, plus one unknown: 0x89 becomes 0x8a
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new FormRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setVersion(9);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord();
    final var current = new FormRecord();
    populate(v1);
    populate(current);

    // when
    final var v1Json = v1.toJson();
    final var currentJson = current.toJson();

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties and it differs between the old and the new class
    final var mapper = new ObjectMapper();
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
  }

  // the same values on both classes, so that every property differs from its default
  private static void populate(
      final io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord record) {
    setRequired(record);
    record.setChecksum(new UnsafeBuffer(CHECKSUM));
    record.setResource(utf8(RESOURCE));
    record.setTenantId("tenant-a");
    record.setDeploymentKey(4_294_967_297L);
    record.setVersionTag("version-tag");
  }

  private static void populate(final FormRecord record) {
    setRequired(record);
    record.setChecksum(new UnsafeBuffer(CHECKSUM));
    record.setResource(utf8(RESOURCE));
    record.setTenantId("tenant-a");
    record.setDeploymentKey(4_294_967_297L);
    record.setVersionTag("version-tag");
  }

  // the properties without a default cannot be written while unset
  private static void setRequired(
      final io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.FormRecord record) {
    record.setFormId(FORM_ID);
    record.setVersion(300);
    record.setFormKey(4_294_967_296L);
    record.setResourceName("form.form");
  }

  private static void setRequired(final FormRecord record) {
    record.setFormId(FORM_ID);
    record.setVersion(300);
    record.setFormKey(4_294_967_296L);
    record.setResourceName("form.form");
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
