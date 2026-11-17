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
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReference} is a
 * frozen copy of the property based implementation the generated codec replaced.
 */
final class DocumentReferenceSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReference();
    final var current = new DocumentReference();
    v1.setDocumentId("document");
    v1.setStoreId("store");
    v1.setContentHash("a value that is longer than forty bytes of utf8 text");
    v1.getMetadata().setFileName("file.txt").setSize(300L);
    current.setDocumentId("document");
    current.setStoreId("store");
    current.setContentHash("a value that is longer than forty bytes of utf8 text");
    current.getMetadata().setFileName("file.txt").setSize(300L);

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
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReference();
    final var current = new DocumentReference();

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
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReference();
    v1.setDocumentId("document");
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new DocumentReference();
    current.setDocumentId("dirty");
    current.setStoreId("dirty");
    current.setContentHash("dirty");
    current.getMetadata().setFileName("dirty").setSize(9L);

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getDocumentId()).isEqualTo("document");
    assertThat(current.getStoreId()).isEqualTo("");
    assertThat(current.getContentHash()).isEqualTo("");
    assertThat(current.getMetadata().getFileName()).isEqualTo("");
    assertThat(current.getMetadata().getSize()).isEqualTo(-1L);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DocumentReference();
    current.setDocumentId("document");
    current.setStoreId("store");
    current.setContentHash("a value that is longer than forty bytes of utf8 text");
    current.getMetadata().setFileName("file.txt").setSize(300L);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReference();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getDocumentId()).isEqualTo("document");
    assertThat(v1.getStoreId()).isEqualTo("store");
    assertThat(v1.getContentHash())
        .isEqualTo("a value that is longer than forty bytes of utf8 text");
    assertThat(v1.getMetadata().getFileName()).isEqualTo("file.txt");
    assertThat(v1.getMetadata().getSize()).isEqualTo(300L);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DocumentReference();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new DocumentReference();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setDocumentId("dirty");
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
        new io.camunda.zeebe.protocol.impl.record.value.document.legacy.DocumentReference();
    final var current = new DocumentReference();
    v1.setDocumentId("document");
    v1.setStoreId("store");
    v1.setContentHash("a value that is longer than forty bytes of utf8 text");
    v1.getMetadata().setFileName("file.txt").setSize(300L);
    current.setDocumentId("document");
    current.setStoreId("store");
    current.setContentHash("a value that is longer than forty bytes of utf8 text");
    current.getMetadata().setFileName("file.txt").setSize(300L);

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
