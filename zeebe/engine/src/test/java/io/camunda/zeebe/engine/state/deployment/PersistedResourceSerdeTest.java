/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link PersistedResourceV1} is a frozen copy of the property based implementation the
 * generated codec replaced.
 */
final class PersistedResourceSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 = new PersistedResourceV1().wrap(populatedRecord());
    final var current = new PersistedResource().wrap(populatedRecord());

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
  void shouldWriteTheSameBytesAsV1ForACopy() {
    // given
    final var v1 = new PersistedResourceV1().wrap(populatedRecord()).copy();
    final var current = new PersistedResource().wrap(populatedRecord()).copy();

    // when
    final var v1Bytes = new ExpandableArrayBuffer();
    final int v1Length = v1.write(v1Bytes, 0);
    final var currentBytes = new ExpandableArrayBuffer();
    final int currentLength = current.write(currentBytes, 0);

    // then
    assertThat(hex(currentBytes, currentLength)).isEqualTo(hex(v1Bytes, v1Length));
  }

  @Test
  void shouldRejectWritingWhileUnsetLikeV1() {
    // given
    final var v1 = new PersistedResourceV1();
    final var current = new PersistedResource();

    // then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 = new PersistedResourceV1().wrap(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new PersistedResource().wrap(otherRecord());

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(BufferUtil.bufferAsString(current.getResourceId())).isEqualTo("resource");
    assertThat(current.getVersion()).isEqualTo(2);
    assertThat(current.getResourceKey()).isEqualTo(31L);
    assertThat(BufferUtil.bufferAsString(current.getChecksum())).isEqualTo("checksum");
    assertThat(BufferUtil.bufferAsString(current.getResourceName())).isEqualTo("res.xml");
    assertThat(current.getTenantId()).isEqualTo("tenant");
    assertThat(current.getDeploymentKey()).isEqualTo(32L);
    assertThat(current.getVersionTag()).isEqualTo("tag");
    assertThat(BufferUtil.bufferAsString(current.getResourceBuffer())).isEqualTo("<xml/>");
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new PersistedResource().wrap(populatedRecord());
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 = new PersistedResourceV1();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(BufferUtil.bufferAsString(v1.getResourceId())).isEqualTo("resource");
    assertThat(v1.getVersion()).isEqualTo(2);
    assertThat(v1.getResourceKey()).isEqualTo(31L);
    assertThat(v1.getTenantId()).isEqualTo("tenant");
    assertThat(v1.getDeploymentKey()).isEqualTo(32L);
    assertThat(BufferUtil.bufferAsString(v1.getResourceBuffer())).isEqualTo("<xml/>");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new PersistedResource().wrap(populatedRecord());
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // The map has nine entries, a header of 0x80 | count, so announce one more.
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new PersistedResource();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.wrap(otherRecord());
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  private static ResourceRecord populatedRecord() {
    return new ResourceRecord()
        .setResourceId("resource")
        .setVersion(2)
        .setResourceKey(31L)
        .setChecksum(BufferUtil.wrapString("checksum"))
        .setResourceName("res.xml")
        .setTenantId("tenant")
        .setDeploymentKey(32L)
        .setVersionTag("tag")
        .setResource(BufferUtil.wrapString("<xml/>"));
  }

  private static ResourceRecord otherRecord() {
    return new ResourceRecord()
        .setResourceId("other")
        .setVersion(1)
        .setResourceKey(1L)
        .setChecksum(BufferUtil.wrapString("other"))
        .setResourceName("other.xml")
        .setTenantId("other")
        .setDeploymentKey(1L)
        .setVersionTag("other")
        .setResource(BufferUtil.wrapString("other"));
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
