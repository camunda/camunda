/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The serialized format must not change, otherwise migration, rollback and mixed version clusters
 * break. {@link io.camunda.zeebe.engine.state.instance.legacy.AwaitProcessInstanceResultMetadata}
 * is a frozen copy of the property based implementation the generated codec replaced.
 */
final class AwaitProcessInstanceResultMetadataSerdeTest {

  @Test
  void shouldWriteTheSameBytesAsV1() {
    // given every property set to a non-default value, with a list long enough to change the
    // msgpack array header and a name longer than 31 bytes
    final var names =
        IntStream.range(0, 20)
            .mapToObj(i -> "variable-%02d-with-a-name-longer-than-31-bytes".formatted(i))
            .toList();
    final var v1 =
        new io.camunda.zeebe.engine.state.instance.legacy.AwaitProcessInstanceResultMetadata();
    v1.setRequestId(1L << 40).setRequestStreamId(300).setFetchVariables(v1Variables(names));
    final var current = new AwaitProcessInstanceResultMetadata();
    current.setRequestId(1L << 40).setRequestStreamId(300).setFetchVariables(variables(names));

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
    final var v1 =
        new io.camunda.zeebe.engine.state.instance.legacy.AwaitProcessInstanceResultMetadata();
    final var current = new AwaitProcessInstanceResultMetadata();

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
    final var v1 =
        new io.camunda.zeebe.engine.state.instance.legacy.AwaitProcessInstanceResultMetadata();
    v1.setRequestId(42L).setRequestStreamId(3).setFetchVariables(v1Variables("a", "b"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = v1.write(bytes, 0);
    final var current = new AwaitProcessInstanceResultMetadata();
    // dirty every property: the list must be replaced, not appended to, by reading
    current.setRequestId(7L).setRequestStreamId(8).setFetchVariables(variables("dirty", "more"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.getRequestId()).isEqualTo(42L);
    assertThat(current.getRequestStreamId()).isEqualTo(3);
    assertThat(names(current.fetchVariables())).containsExactly("a", "b");
  }

  @Test
  void shouldClearTheListWhenReadingAValueWithoutVariables() {
    // given a value without variables, read into an instance that has some
    final var source =
        new io.camunda.zeebe.engine.state.instance.legacy.AwaitProcessInstanceResultMetadata();
    source.setRequestId(42L).setRequestStreamId(3);
    final var bytes = new ExpandableArrayBuffer();
    final int length = source.write(bytes, 0);
    final var current = new AwaitProcessInstanceResultMetadata();
    current.setFetchVariables(variables("dirty"));

    // when
    current.wrap(bytes, 0, length);

    // then
    assertThat(current.fetchVariables()).isEmpty();
    assertThat(current.getRequestId()).isEqualTo(42L);
  }

  @Test
  void shouldBeReadByV1() {
    // given
    final var current = new AwaitProcessInstanceResultMetadata();
    current.setRequestId(42L).setRequestStreamId(3).setFetchVariables(variables("a", "b"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = current.write(bytes, 0);

    // when
    final var v1 =
        new io.camunda.zeebe.engine.state.instance.legacy.AwaitProcessInstanceResultMetadata();
    v1.wrap(bytes, 0, length);

    // then
    assertThat(v1.getRequestId()).isEqualTo(42L);
    assertThat(v1.getRequestStreamId()).isEqualTo(3);
    final List<String> v1Names = new ArrayList<>();
    v1.fetchVariables()
        .forEach(variable -> v1Names.add(BufferUtil.bufferAsString(variable.getValue())));
    assertThat(v1Names).containsExactly("a", "b");
  }

  @Test
  void shouldKeepUnknownPropertiesOfNewerVersions() {
    // given a value that carries a property this version does not know
    final var source = new AwaitProcessInstanceResultMetadata();
    source.setRequestId(42L).setRequestStreamId(3).setFetchVariables(variables("a"));
    final var stored = new ExpandableArrayBuffer();
    final int storedLength = source.write(stored, 0);
    final var future = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(future, 0);
    int futureLength = writer.writeString(utf8("futureProperty"));
    futureLength += writer.writeInteger(1234);
    stored.putBytes(storedLength, future, 0, futureLength);
    // the map has three entries, plus one unknown: 0x83 becomes 0x84
    stored.putByte(0, (byte) (stored.getByte(0) + 1));

    // when read, modified and written again
    final var instance = new AwaitProcessInstanceResultMetadata();
    instance.wrap(stored, 0, storedLength + futureLength);
    instance.setRequestStreamId(5);
    final var rewritten = new ExpandableArrayBuffer();
    final int rewrittenLength = instance.write(rewritten, 0);

    // then the unknown entry is still the last thing in the map
    assertThat(rewrittenLength).isEqualTo(instance.getLength());
    assertThat(hex(rewritten, rewrittenLength)).endsWith(hex(future, futureLength));
  }

  /** Builds the input of {@code setFetchVariables}, the same way the record of a command does. */
  private static List<DirectBuffer> variables(final String... names) {
    return variables(List.of(names));
  }

  private static List<DirectBuffer> variables(final List<String> names) {
    return new ProcessInstanceCreationRecord().setFetchVariables(names).fetchVariables();
  }

  private static ArrayProperty<StringValue> v1Variables(final String... names) {
    return v1Variables(List.of(names));
  }

  private static ArrayProperty<StringValue> v1Variables(final List<String> names) {
    final var property = new ArrayProperty<>("fetchVariables", StringValue::new);
    names.forEach(name -> property.add().wrap(BufferUtil.wrapString(name)));
    return property;
  }

  private static List<String> names(final List<DirectBuffer> buffers) {
    return buffers.stream().map(BufferUtil::bufferAsString).toList();
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
