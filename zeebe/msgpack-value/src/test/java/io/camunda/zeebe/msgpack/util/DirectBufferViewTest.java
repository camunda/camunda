/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.util;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class DirectBufferViewTest {

  @Test
  void shouldBeEqualForSameContentAndSameLength() {
    final DirectBufferView first = new DirectBufferView();
    final DirectBufferView second = new DirectBufferView();

    final DirectBuffer firstBuffer = new UnsafeBuffer("FOO".getBytes());
    final DirectBuffer secondBuffer = new UnsafeBuffer("FOO".getBytes());

    first.wrap(firstBuffer, 0, firstBuffer.capacity());
    second.wrap(secondBuffer, 0, secondBuffer.capacity());

    assertThat(first).isEqualTo(second);
    assertThat(second).isEqualTo(first);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void shouldBeEqualWhenWrappingRegionWithOffsetAndLength() {
    final DirectBufferView first = new DirectBufferView();
    final DirectBufferView second = new DirectBufferView();

    final DirectBuffer bufferWithPrefix = new UnsafeBuffer("PREFIX_FOO".getBytes());
    final DirectBuffer plainBuffer = new UnsafeBuffer("FOO".getBytes());

    final int offset = "PREFIX_".getBytes().length;
    final int length = "FOO".getBytes().length;

    first.wrap(bufferWithPrefix, offset, length);
    second.wrap(plainBuffer, 0, plainBuffer.capacity());

    assertThat(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
  }

  @Test
  void shouldNotBeEqualForDifferentContent() {
    final DirectBufferView first = new DirectBufferView();
    final DirectBufferView second = new DirectBufferView();

    final DirectBuffer firstBuffer = new UnsafeBuffer("FOO".getBytes());
    final DirectBuffer secondBuffer = new UnsafeBuffer("BAR".getBytes());

    first.wrap(firstBuffer, 0, firstBuffer.capacity());
    second.wrap(secondBuffer, 0, secondBuffer.capacity());

    assertThat(first).isNotEqualTo(second);
    assertThat(first).isNotEqualTo(null);
    assertThat(first).isNotEqualTo("FOO");
  }

  @Test
  void shouldHaveStableHashCodeForSameWrappedRegion() {
    final DirectBufferView view = new DirectBufferView();
    final DirectBuffer buffer = new UnsafeBuffer("FOO".getBytes());

    view.wrap(buffer, 0, buffer.capacity());

    final int firstHash = view.hashCode();
    final int secondHash = view.hashCode();

    assertThat(firstHash).isEqualTo(secondHash);
  }
}
