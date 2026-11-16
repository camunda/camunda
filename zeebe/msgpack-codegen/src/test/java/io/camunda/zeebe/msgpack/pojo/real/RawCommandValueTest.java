/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.real;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * A class that holds a value whose type is only known at runtime, like a command that is buffered
 * or distributed together with its value type. The legacy class keeps it in an untyped {@link
 * UnpackedObject} that reads whatever map it is given; the generated class keeps the encoded map in
 * a {@code raw} field. Both store the same bytes, as long as the default of the raw field is an
 * empty map, which is what the legacy empty object writes.
 */
final class RawCommandValueTest {

  @Test
  void shouldStoreTheSameBytesAsTheLegacyHolderByDefault() {
    // given
    final var legacy = new LegacyHolder();
    final var generated = new RawCommandHolder();

    // when
    final var bytes = write(generated);

    // then
    assertThat(bytes).isEqualTo(write(legacy));
    assertThat(generated.getLength()).isEqualTo(legacy.getLength());
  }

  @Test
  void shouldStoreTheSameBytesAsTheLegacyHolderWithAValue() {
    // given
    final var child = filledChild();
    final var legacy = new LegacyHolder();
    legacy.intent.setValue(7);
    legacy.setCommandValue(child);
    final var generated = new RawCommandHolder();
    generated.intent = 7;
    generated.setCommandValue(child);

    // when
    final var bytes = write(generated);

    // then
    assertThat(bytes).isEqualTo(write(legacy));
    assertThat(generated.getLength()).isEqualTo(legacy.getLength());
  }

  @Test
  void shouldReadTheValueWrittenByTheLegacyHolder() {
    // given
    final var legacy = new LegacyHolder();
    legacy.intent.setValue(7);
    legacy.setCommandValue(filledChild());
    final var bytes = write(legacy);
    final var generated = new RawCommandHolder();
    generated.setCommandValue(new LegacyChild());

    // when
    generated.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then the raw field holds the encoded value, which the concrete class reads
    assertThat(generated.intent).isEqualTo(7);
    final var read = new LegacyChild();
    read.wrap(generated.commandValue);
    assertThat(write(read)).isEqualTo(write(filledChild()));
  }

  @Test
  void shouldBeReadByTheLegacyHolder() {
    // given
    final var generated = new RawCommandHolder();
    generated.intent = 7;
    generated.setCommandValue(filledChild());
    final var bytes = write(generated);

    // when
    final var legacy = new LegacyHolder();
    legacy.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then
    assertThat(legacy.intent.getValue()).isEqualTo(7);
    assertThat(write(legacy)).isEqualTo(bytes);
  }

  @Test
  void shouldRestoreTheEmptyValueWhenReadingBytesWithoutIt() {
    // given
    final var bytes = write(new LegacyHolder());
    final var generated = new RawCommandHolder();
    generated.setCommandValue(filledChild());

    // when
    generated.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then
    assertThat(write(generated)).isEqualTo(bytes);
  }

  private static LegacyChild filledChild() {
    final var child = new LegacyChild();
    child.fill(3);
    return child;
  }

  private static byte[] write(final BufferWriter writer) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = writer.getLength();
    writer.write(buffer, 0);
    final var bytes = new byte[length];
    buffer.getBytes(0, bytes);
    return bytes;
  }

  /** What the old classes did: an untyped object that stores any map it reads. */
  private static final class LegacyBag extends UnpackedObject {
    LegacyBag() {
      super(10);
    }
  }

  private static final class LegacyHolder extends UnpackedObject {
    private final IntegerProperty intent = new IntegerProperty("intent", -1);
    private final ObjectProperty<LegacyBag> commandValue =
        new ObjectProperty<>("commandValue", new LegacyBag());

    LegacyHolder() {
      super(2);
      declareProperty(intent).declareProperty(commandValue);
    }

    void setCommandValue(final BufferWriter value) {
      final var buffer = new UnsafeBuffer(new byte[value.getLength()]);
      value.write(buffer, 0);
      commandValue.getValue().read(new MsgPackReader().wrap(buffer, 0, buffer.capacity()));
    }
  }
}
