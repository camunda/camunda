/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link MappingRuleRecordV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class MappingRuleRecordSerdeTest {

  private static final String MAPPING_RULE_ID = "a mapping rule id that is longer than forty bytes";
  private static final String CLAIM_NAME = "a claim name that is also longer than forty bytes";
  private static final String CLAIM_VALUE = "claim value";
  private static final String NAME = "a name with some more text in it";

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new MappingRuleRecordV1();
    final var current = new MappingRuleRecord();
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
    final var v1 = new MappingRuleRecordV1();
    final var current = new MappingRuleRecord();
    v1.setMappingRuleId(MAPPING_RULE_ID);
    current.setMappingRuleId(MAPPING_RULE_ID);

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
    final var v1 = new MappingRuleRecordV1();
    final var current = new MappingRuleRecord();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new MappingRuleRecordV1();
    setAllProperties(v1);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MappingRuleRecord();
    current
        .setMappingRuleKey(7L)
        .setMappingRuleId("dirty")
        .setClaimName("dirty")
        .setClaimValue("dirty")
        .setName("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getMappingRuleKey()).isEqualTo(42L);
    assertThat(current.getMappingRuleId()).isEqualTo(MAPPING_RULE_ID);
    assertThat(current.getClaimName()).isEqualTo(CLAIM_NAME);
    assertThat(current.getClaimValue()).isEqualTo(CLAIM_VALUE);
    assertThat(current.getName()).isEqualTo(NAME);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given a value with defaults everywhere except the property without a default
    final var v1 = new MappingRuleRecordV1();
    v1.setMappingRuleId(MAPPING_RULE_ID);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new MappingRuleRecord();
    setAllProperties(current);

    // when
    current.wrap(bytes, 0, length);

    // then
    final var defaults = new MappingRuleRecordV1();
    defaults.setMappingRuleId(MAPPING_RULE_ID);
    assertThat(current.getMappingRuleKey()).isEqualTo(defaults.getMappingRuleKey());
    assertThat(current.getClaimName()).isEqualTo(defaults.getClaimName());
    assertThat(current.getClaimValue()).isEqualTo(defaults.getClaimValue());
    assertThat(current.getName()).isEqualTo(defaults.getName());
  }

  @Test
  void shouldFailToReadWithoutTheKeyLikeV1() {
    // given an empty map
    final var bytes = new ExpandableArrayBuffer(1);
    bytes.putByte(0, (byte) 0x80);

    // when
    final var v1Failure = catchThrowable(() -> new MappingRuleRecordV1().wrap(bytes, 0, 1));
    final var failure = catchThrowable(() -> new MappingRuleRecord().wrap(bytes, 0, 1));

    // then the same failure as V1
    assertThat(failure.getCause()).isExactlyInstanceOf(v1Failure.getCause().getClass());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new MappingRuleRecord();
    setAllProperties(current);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new MappingRuleRecordV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getMappingRuleKey()).isEqualTo(42L);
    assertThat(v1.getMappingRuleId()).isEqualTo(MAPPING_RULE_ID);
    assertThat(v1.getClaimName()).isEqualTo(CLAIM_NAME);
    assertThat(v1.getClaimValue()).isEqualTo(CLAIM_VALUE);
    assertThat(v1.getName()).isEqualTo(NAME);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new MappingRuleRecord();
    setAllProperties(source);
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has five entries, plus one unknown: 0x85 becomes 0x86
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new MappingRuleRecord();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setName("modified");
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  @Test
  void shouldProduceTheSameJsonAsV1() throws Exception {
    // given
    final var v1 = new MappingRuleRecordV1();
    final var current = new MappingRuleRecord();
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

  private static void setAllProperties(final MappingRuleRecordV1 record) {
    record
        .setMappingRuleKey(42L)
        .setMappingRuleId(MAPPING_RULE_ID)
        .setClaimName(CLAIM_NAME)
        .setClaimValue(CLAIM_VALUE)
        .setName(NAME);
  }

  private static void setAllProperties(final MappingRuleRecord record) {
    record
        .setMappingRuleKey(42L)
        .setMappingRuleId(MAPPING_RULE_ID)
        .setClaimName(CLAIM_NAME)
        .setClaimValue(CLAIM_VALUE)
        .setName(NAME);
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
