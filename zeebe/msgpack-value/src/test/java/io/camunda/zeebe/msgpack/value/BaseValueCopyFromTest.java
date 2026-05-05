/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.value;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.POJO.POJOEnum;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests {@link BaseValue#copyFrom} for every concrete BaseValue type. */
final class BaseValueCopyFromTest {

  @Nested
  class LongValueCopy {
    @Test
    void shouldCopyValue() {
      // given
      final var source = new LongValue(42L);
      final var target = new LongValue();

      // when
      target.copyFrom(source);

      // then
      assertThat(target.getValue()).isEqualTo(42L);
    }

    @Test
    void shouldBeIndependent() {
      // given
      final var source = new LongValue(42L);
      final var target = new LongValue();
      target.copyFrom(source);

      // when — mutate source
      source.setValue(99L);

      // then — target unchanged
      assertThat(target.getValue()).isEqualTo(42L);
    }
  }

  @Nested
  class IntegerValueCopy {
    @Test
    void shouldCopyValue() {
      final var source = new IntegerValue(123);
      final var target = new IntegerValue();
      target.copyFrom(source);
      assertThat(target.getValue()).isEqualTo(123);
    }
  }

  @Nested
  class BooleanValueCopy {
    @Test
    void shouldCopyTrue() {
      final var source = new BooleanValue(true);
      final var target = new BooleanValue();
      target.copyFrom(source);
      assertThat(target.getValue()).isTrue();
    }

    @Test
    void shouldCopyFalse() {
      final var source = new BooleanValue(false);
      final var target = new BooleanValue(true);
      target.copyFrom(source);
      assertThat(target.getValue()).isFalse();
    }
  }

  @Nested
  class StringValueCopy {
    @Test
    void shouldCopyValue() {
      final var source = new StringValue("hello");
      final var target = new StringValue();
      target.copyFrom(source);
      assertThat(target.toString()).isEqualTo("hello");
    }

    @Test
    void shouldBeIndependent() {
      final var source = new StringValue("hello");
      final var target = new StringValue();
      target.copyFrom(source);
      source.wrap("world");
      assertThat(target.toString()).isEqualTo("hello");
    }
  }

  @Nested
  class BinaryValueCopy {
    @Test
    void shouldCopyValue() {
      final var data = new UnsafeBuffer(new byte[] {1, 2, 3, 4});
      final var source = new BinaryValue(data, 0, data.capacity());
      final var target = new BinaryValue();
      target.copyFrom(source);
      assertThat(target.getValue()).isEqualTo(data);
    }
  }

  @Nested
  class PackedValueCopy {
    @Test
    void shouldCopyValue() {
      final var data = new UnsafeBuffer(new byte[] {0x01, 0x02, 0x03});
      final var source = new PackedValue(data, 0, data.capacity());
      final var target = new PackedValue();
      target.copyFrom(source);
      assertThat(target.getValue()).isEqualTo(data);
    }
  }

  @Nested
  class EnumValueCopy {
    @Test
    void shouldCopyValue() {
      final var source = new EnumValue<>(POJOEnum.class, POJOEnum.BAR);
      final var target = new EnumValue<>(POJOEnum.class);
      target.copyFrom(source);
      assertThat(target.getValue()).isEqualTo(POJOEnum.BAR);
    }
  }

  @Nested
  class ArrayValueCopy {
    @Test
    void shouldCopyValues() {
      // given
      final var source = new ArrayValue<>(LongValue::new);
      source.add().setValue(10);
      source.add().setValue(20);
      source.add().setValue(30);

      final var target = new ArrayValue<>(LongValue::new);

      // when
      target.copyFrom(source);

      // then
      assertThat(target.size()).isEqualTo(3);
      assertThat(target.get(0).getValue()).isEqualTo(10);
      assertThat(target.get(1).getValue()).isEqualTo(20);
      assertThat(target.get(2).getValue()).isEqualTo(30);
    }

    @Test
    void shouldBeIndependent() {
      final var source = new ArrayValue<>(LongValue::new);
      source.add().setValue(10);

      final var target = new ArrayValue<>(LongValue::new);
      target.copyFrom(source);

      // mutate source
      source.add().setValue(99);

      assertThat(target.size()).isEqualTo(1);
      assertThat(target.get(0).getValue()).isEqualTo(10);
    }
  }

  @Nested
  class DeltaEncodedLongArrayValueCopy {
    @Test
    void shouldCopyValues() {
      final var source = new DeltaEncodedLongArrayValue();
      source.setValues(new long[] {4, 13, 15, 16, 20});

      final var target = new DeltaEncodedLongArrayValue();
      target.copyFrom(source);

      assertThat(target.getValues()).containsExactly(4, 13, 15, 16, 20);
    }

    @Test
    void shouldBeIndependent() {
      final var source = new DeltaEncodedLongArrayValue();
      source.setValues(new long[] {1, 2, 3});

      final var target = new DeltaEncodedLongArrayValue();
      target.copyFrom(source);

      source.getValues()[0] = 999;
      assertThat(target.getValues()[0]).isEqualTo(1);
    }
  }
}
