/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.agentdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link DbAgentDefinitionV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class DbAgentDefinitionSerdeTest {

  private static final String LONG_NAME = "a name that is longer than forty bytes of utf8 text";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new DbAgentDefinitionV1();
    final var current = new DbAgentDefinition();
    v1.getRecord().setAgentDefinitionKey(42L).setName(LONG_NAME).setTenantId("tenant");
    current.getRecord().setAgentDefinitionKey(42L).setName(LONG_NAME).setTenantId("tenant");

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
    final var v1 = new DbAgentDefinitionV1();
    final var current = new DbAgentDefinition();

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
    final var v1 = new DbAgentDefinitionV1();
    v1.getRecord().setAgentDefinitionKey(42L).setName(LONG_NAME).setTenantId("tenant");
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DbAgentDefinition();
    current.getRecord().setAgentDefinitionKey(7L).setName("dirty").setTenantId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRecord().getAgentDefinitionKey()).isEqualTo(42L);
    assertThat(current.getRecord().getName()).isEqualTo(LONG_NAME);
    assertThat(current.getRecord().getTenantId()).isEqualTo("tenant");
  }

  @Test
  void shouldResetToDefaultsWhenReadingBytesWithDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length = new DbAgentDefinitionV1().write(bytes, 0);
    final var current = new DbAgentDefinition();
    current.getRecord().setAgentDefinitionKey(7L).setName("dirty").setTenantId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new DbAgentDefinitionV1();
    assertThat(current.getRecord().getAgentDefinitionKey())
        .isEqualTo(defaults.getRecord().getAgentDefinitionKey());
    assertThat(current.getRecord().getName()).isEqualTo(defaults.getRecord().getName());
    assertThat(current.getRecord().getTenantId()).isEqualTo(defaults.getRecord().getTenantId());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DbAgentDefinition();
    current.getRecord().setAgentDefinitionKey(42L).setName(LONG_NAME).setTenantId("tenant");
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new DbAgentDefinitionV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRecord().getAgentDefinitionKey()).isEqualTo(42L);
    assertThat(v1.getRecord().getName()).isEqualTo(LONG_NAME);
    assertThat(v1.getRecord().getTenantId()).isEqualTo("tenant");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DbAgentDefinition();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // one entry (0x81) becomes two entries (0x82): the map has fewer than 16 entries
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new DbAgentDefinition();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.getRecord().setName(LONG_NAME);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldNotReferenceTheBufferItWasReadFrom() {
    // given
    final var instance = new DbAgentDefinition();
    instance.getRecord().setName(LONG_NAME);
    final var buffer = new ExpandableArrayBuffer();
    final int length = instance.write(buffer, 0);
    final var read = new DbAgentDefinition();
    read.wrap(buffer, 0, length);

    // when the buffer is reused
    buffer.setMemory(0, buffer.capacity(), (byte) 0);

    // then
    assertThat(read.getRecord().getName()).isEqualTo(LONG_NAME);
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
