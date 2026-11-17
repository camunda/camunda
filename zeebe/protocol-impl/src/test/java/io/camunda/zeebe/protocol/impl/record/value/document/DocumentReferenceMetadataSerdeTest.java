/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReferenceMetadata} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class DocumentReferenceMetadataSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReferenceMetadata();
    final var current = new DocumentReferenceMetadata();
    v1.setContentType("text/plain");
    v1.setFileName("a value that is longer than forty bytes of utf8 text");
    v1.setExpiresAt(70000L);
    v1.setSize(300L);
    v1.setProcessDefinitionId("process");
    v1.setProcessInstanceKey(70000L);
    v1.setCustomProperties(Map.<String, Object>of("key", "value"));
    current.setContentType("text/plain");
    current.setFileName("a value that is longer than forty bytes of utf8 text");
    current.setExpiresAt(70000L);
    current.setSize(300L);
    current.setProcessDefinitionId("process");
    current.setProcessInstanceKey(70000L);
    current.setCustomProperties(Map.<String, Object>of("key", "value"));

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(currentLength)
        .isEqualTo(current.getLength())
        .isEqualTo(LegacyObjectValues.length(v1));
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldWriteTheSameBytesAsV1WithDefaults() {
    // given only what cannot be written unset, everything else stays default
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReferenceMetadata();
    final var current = new DocumentReferenceMetadata();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = LegacyObjectValues.write(v1, v1Bytes);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReferenceMetadata();
    v1.setContentType("text/plain");
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new DocumentReferenceMetadata();
    current.setContentType("dirty");
    current.setFileName("dirty");
    current.setExpiresAt(9L);
    current.setSize(9L);
    current.setProcessDefinitionId("dirty");
    current.setProcessInstanceKey(9L);
    current.setCustomProperties(Map.<String, Object>of("dirty", 1));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getContentType()).isEqualTo("text/plain");
    assertThat(current.getFileName()).isEqualTo("");
    assertThat(current.getExpiresAt()).isEqualTo(-1L);
    assertThat(current.getSize()).isEqualTo(-1L);
    assertThat(current.getProcessDefinitionId()).isEqualTo("");
    assertThat(current.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(current.getCustomProperties()).isEqualTo(Map.of());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DocumentReferenceMetadata();
    current.setContentType("text/plain");
    current.setFileName("a value that is longer than forty bytes of utf8 text");
    current.setExpiresAt(70000L);
    current.setSize(300L);
    current.setProcessDefinitionId("process");
    current.setProcessInstanceKey(70000L);
    current.setCustomProperties(Map.<String, Object>of("key", "value"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReferenceMetadata();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getContentType()).isEqualTo("text/plain");
    assertThat(v1.getFileName()).isEqualTo("a value that is longer than forty bytes of utf8 text");
    assertThat(v1.getExpiresAt()).isEqualTo(70000L);
    assertThat(v1.getSize()).isEqualTo(300L);
    assertThat(v1.getProcessDefinitionId()).isEqualTo("process");
    assertThat(v1.getProcessInstanceKey()).isEqualTo(70000L);
    assertThat(v1.getCustomProperties()).isEqualTo(Map.of("key", "value"));
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DocumentReferenceMetadata();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new DocumentReferenceMetadata();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setContentType("dirty");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReferenceMetadata();
    final var current = new DocumentReferenceMetadata();
    v1.setContentType("text/plain");
    v1.setFileName("a value that is longer than forty bytes of utf8 text");
    v1.setExpiresAt(70000L);
    v1.setSize(300L);
    v1.setProcessDefinitionId("process");
    v1.setProcessInstanceKey(70000L);
    v1.setCustomProperties(Map.<String, Object>of("key", "value"));
    current.setContentType("text/plain");
    current.setFileName("a value that is longer than forty bytes of utf8 text");
    current.setExpiresAt(70000L);
    current.setSize(300L);
    current.setProcessDefinitionId("process");
    current.setProcessInstanceKey(70000L);
    current.setCustomProperties(Map.<String, Object>of("key", "value"));

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.writeValueAsString(v1);
    final var currentJson = mapper.writeValueAsString(current);

    // then exporters see the same document; compare the trees, Jackson does not guarantee the order
    // of the properties
    assertThat(mapper.readTree(currentJson)).isEqualTo(mapper.readTree(v1Json));
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
