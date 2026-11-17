/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.LegacyObjectValues;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link
 * io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy.PolicyAuthorizationDeleteEntry}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class PolicyAuthorizationDeleteEntrySerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy
            .PolicyAuthorizationDeleteEntry();
    final var current = new PolicyAuthorizationDeleteEntry();
    v1.setOwnerType(AuthorizationOwnerType.USER);
    v1.setOwnerId("a value that is longer than forty bytes of utf8 text");
    v1.setResourceType(AuthorizationResourceType.DOCUMENT);
    v1.setResourceMatcher(AuthorizationResourceMatcher.ID);
    v1.setResourceId("resource");
    v1.setResourcePropertyName("property");
    current.setOwnerType(AuthorizationOwnerType.USER);
    current.setOwnerId("a value that is longer than forty bytes of utf8 text");
    current.setResourceType(AuthorizationResourceType.DOCUMENT);
    current.setResourceMatcher(AuthorizationResourceMatcher.ID);
    current.setResourceId("resource");
    current.setResourcePropertyName("property");

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
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy
            .PolicyAuthorizationDeleteEntry();
    final var current = new PolicyAuthorizationDeleteEntry();

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
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy
            .PolicyAuthorizationDeleteEntry();
    v1.setOwnerType(AuthorizationOwnerType.USER);
    final var bytes = new ExpandableArrayBuffer();
    final int length = LegacyObjectValues.write(v1, bytes);
    final var current = new PolicyAuthorizationDeleteEntry();
    current.setOwnerType(AuthorizationOwnerType.ROLE);
    current.setOwnerId("dirty");
    current.setResourceType(AuthorizationResourceType.ROLE);
    current.setResourceMatcher(AuthorizationResourceMatcher.ANY);
    current.setResourceId("dirty");
    current.setResourcePropertyName("dirty");

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getOwnerType()).isEqualTo(AuthorizationOwnerType.USER);
    assertThat(current.getOwnerId()).isEqualTo("");
    assertThat(current.getResourceType()).isEqualTo(AuthorizationResourceType.UNSPECIFIED);
    assertThat(current.getResourceMatcher()).isEqualTo(AuthorizationResourceMatcher.UNSPECIFIED);
    assertThat(current.getResourceId()).isEqualTo("");
    assertThat(current.getResourcePropertyName()).isEqualTo("");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PolicyAuthorizationDeleteEntry();
    current.setOwnerType(AuthorizationOwnerType.USER);
    current.setOwnerId("a value that is longer than forty bytes of utf8 text");
    current.setResourceType(AuthorizationResourceType.DOCUMENT);
    current.setResourceMatcher(AuthorizationResourceMatcher.ID);
    current.setResourceId("resource");
    current.setResourcePropertyName("property");
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy
            .PolicyAuthorizationDeleteEntry();
    LegacyObjectValues.read(v1, bytes, length);

    // then
    assertThat(v1.getOwnerType()).isEqualTo(AuthorizationOwnerType.USER);
    assertThat(v1.getOwnerId()).isEqualTo("a value that is longer than forty bytes of utf8 text");
    assertThat(v1.getResourceType()).isEqualTo(AuthorizationResourceType.DOCUMENT);
    assertThat(v1.getResourceMatcher()).isEqualTo(AuthorizationResourceMatcher.ID);
    assertThat(v1.getResourceId()).isEqualTo("resource");
    assertThat(v1.getResourcePropertyName()).isEqualTo("property");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PolicyAuthorizationDeleteEntry();
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PolicyAuthorizationDeleteEntry();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setOwnerType(AuthorizationOwnerType.ROLE);
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
        new io.camunda.zeebe.protocol.impl.record.value.policyupdate.legacy
            .PolicyAuthorizationDeleteEntry();
    final var current = new PolicyAuthorizationDeleteEntry();
    v1.setOwnerType(AuthorizationOwnerType.USER);
    v1.setOwnerId("a value that is longer than forty bytes of utf8 text");
    v1.setResourceType(AuthorizationResourceType.DOCUMENT);
    v1.setResourceMatcher(AuthorizationResourceMatcher.ID);
    v1.setResourceId("resource");
    v1.setResourcePropertyName("property");
    current.setOwnerType(AuthorizationOwnerType.USER);
    current.setOwnerId("a value that is longer than forty bytes of utf8 text");
    current.setResourceType(AuthorizationResourceType.DOCUMENT);
    current.setResourceMatcher(AuthorizationResourceMatcher.ID);
    current.setResourceId("resource");
    current.setResourcePropertyName("property");

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
