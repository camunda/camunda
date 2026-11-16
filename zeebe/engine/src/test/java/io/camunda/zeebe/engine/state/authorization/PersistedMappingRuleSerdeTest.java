/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

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
 * break. {@link PersistedMappingRuleV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class PersistedMappingRuleSerdeTest {

  private static final String LONG_VALUE = "a claim value that is longer than forty bytes of text";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new PersistedMappingRuleV1();
    final var current = new PersistedMappingRule();
    v1.setMappingRuleKey(42L).setClaimName("claim").setClaimValue(LONG_VALUE);
    v1.setName("name").setMappingRuleId("rule-id");
    current.setMappingRuleKey(42L).setClaimName("claim").setClaimValue(LONG_VALUE);
    current.setName("name").setMappingRuleId("rule-id");

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
    final var v1 = new PersistedMappingRuleV1();
    final var current = new PersistedMappingRule();

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
    final var v1 = new PersistedMappingRuleV1();
    v1.setMappingRuleKey(42L).setClaimName("claim").setClaimValue(LONG_VALUE);
    v1.setName("name").setMappingRuleId("rule-id");
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedMappingRule();
    current.setMappingRuleKey(7L).setClaimName("dirty").setClaimValue("dirty");
    current.setName("dirty").setMappingRuleId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getMappingRuleKey()).isEqualTo(42L);
    assertThat(current.getClaimName()).isEqualTo("claim");
    assertThat(current.getClaimValue()).isEqualTo(LONG_VALUE);
    assertThat(current.getName()).isEqualTo("name");
    assertThat(current.getMappingRuleId()).isEqualTo("rule-id");
  }

  @Test
  void shouldResetToDefaultsWhenReadingBytesWithDefaults() {
    // given
    final var bytes = new ExpandableArrayBuffer();
    final int length = new PersistedMappingRuleV1().write(bytes, 0);
    final var current = new PersistedMappingRule();
    current.setMappingRuleKey(7L).setClaimName("dirty").setClaimValue("dirty");
    current.setName("dirty").setMappingRuleId("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new PersistedMappingRuleV1();
    assertThat(current.getMappingRuleKey()).isEqualTo(defaults.getMappingRuleKey());
    assertThat(current.getClaimName()).isEqualTo(defaults.getClaimName());
    assertThat(current.getClaimValue()).isEqualTo(defaults.getClaimValue());
    assertThat(current.getName()).isEqualTo(defaults.getName());
    assertThat(current.getMappingRuleId()).isEqualTo(defaults.getMappingRuleId());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedMappingRule();
    current.setMappingRuleKey(42L).setClaimName("claim").setClaimValue(LONG_VALUE);
    current.setName("name").setMappingRuleId("rule-id");
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedMappingRuleV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getMappingRuleKey()).isEqualTo(42L);
    assertThat(v1.getClaimName()).isEqualTo("claim");
    assertThat(v1.getClaimValue()).isEqualTo(LONG_VALUE);
    assertThat(v1.getName()).isEqualTo("name");
    assertThat(v1.getMappingRuleId()).isEqualTo("rule-id");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedMappingRule();
    source.setMappingRuleKey(42L).setName("name");
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // one entry (0x85 for five declared entries) becomes six entries (0x86)
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedMappingRule();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setClaimValue(LONG_VALUE);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
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
