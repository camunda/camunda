/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class DeploymentResourceSerdeTest {

  private static final String NAME = "a-resource-name-that-is-longer-than-thirty-two-bytes.bpmn";
  private static final byte[] RESOURCE = largeResource();

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource();
    final var current = new DeploymentResource();
    v1.setResourceName(NAME).setResource(RESOURCE);
    current.setResourceName(NAME).setResource(RESOURCE);

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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource();
    final var current = new DeploymentResource();
    v1.setResource(utf8("<definitions/>"));
    current.setResource(utf8("<definitions/>"));

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
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource();
    final var current = new DeploymentResource();

    // when / then
    assertThatThrownBy(() -> v1.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(() -> current.write(new ExpandableArrayBuffer(), 0))
        .isInstanceOf(MsgpackPropertyException.class);
    assertThatThrownBy(current::getResource).isInstanceOf(MsgpackPropertyException.class);
  }

  @Test
  void shouldFailToReadWithoutTheResourceLikeV1() {
    // given a map with the resource name only
    final var bytes = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(bytes, 0);
    writer.writeMapHeader(1);
    writer.writeString(utf8("resourceName"));
    writer.writeString(utf8("name"));
    final int length = writer.getOffset();

    // when / then
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource();
    assertThatThrownBy(() -> v1.wrap(bytes, 0, length)).isInstanceOf(RuntimeException.class);
    final var current = new DeploymentResource();
    assertThatThrownBy(() -> current.wrap(bytes, 0, length)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldReadBytesWrittenByV1() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource();
    v1.setResourceName(NAME).setResource(RESOURCE);
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DeploymentResource();
    current.setResourceName("dirty.bpmn").setResource(utf8("dirty"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getResourceName()).isEqualTo(NAME);
    assertThat(current.getResource()).isEqualTo(RESOURCE);
  }

  @Test
  void shouldRestoreDefaultsWhenReadingBytesWrittenByV1WithDefaults() {
    // given
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource();
    v1.setResource(utf8("<definitions/>"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new DeploymentResource();
    current.setResourceName("dirty.bpmn").setResource(utf8("dirty"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getResourceName()).isEqualTo(v1.getResourceName()).isEqualTo("resource");
    assertThat(current.getResource()).isEqualTo(v1.getResource());
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new DeploymentResource();
    current.setResourceName(NAME).setResource(RESOURCE);
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getResourceName()).isEqualTo(NAME);
    assertThat(v1.getResource()).isEqualTo(RESOURCE);
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new DeploymentResource();
    source.setResource(utf8("<definitions/>"));
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new DeploymentResource();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setResourceName("modified.bpmn");
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
        new io.camunda.zeebe.protocol.impl.record.value.deployment.legacy.DeploymentResource();
    final var current = new DeploymentResource();
    v1.setResourceName(NAME).setResource(RESOURCE);
    current.setResourceName(NAME).setResource(RESOURCE);

    // when
    final var mapper = new ObjectMapper();
    final var v1Json = mapper.valueToTree(v1);
    final var currentJson = mapper.valueToTree(current);

    // then exporters see the same document
    assertThat(currentJson).isEqualTo(v1Json);
  }

  private static byte[] largeResource() {
    final var resource = new byte[300];
    Arrays.fill(resource, (byte) 7);
    return resource;
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
