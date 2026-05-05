/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.POJO.POJOEnum;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link UnpackedObject#copyTo(UnpackedObject)} — property-level copy without msgpack
 * serialization.
 */
final class UnpackedObjectCopyTest {

  @Test
  void shouldCopyAllProperties() {
    // given — a fully populated POJO
    final var source = new POJO();
    source.setEnum(POJOEnum.BAR);
    source.setLong(42L);
    source.setInt(7);
    source.setString(new UnsafeBuffer("hello".getBytes()));
    source.setBinary(new UnsafeBuffer(new byte[] {1, 2, 3}));
    source.setPacked(new UnsafeBuffer(new byte[] {0x04}));
    source.nestedObject().setLong(99L);

    final var target = new POJO();

    // when
    source.copyTo(target);

    // then
    assertThat(target.getEnum()).isEqualTo(POJOEnum.BAR);
    assertThat(target.getLong()).isEqualTo(42L);
    assertThat(target.getInt()).isEqualTo(7);
    assertThat(target.getString()).isEqualTo(new UnsafeBuffer("hello".getBytes()));
    assertThat(target.getBinary()).isEqualTo(new UnsafeBuffer(new byte[] {1, 2, 3}));
    assertThat(target.nestedObject().getLong()).isEqualTo(99L);
  }

  @Test
  void shouldCopyFromAnotherUnpackedObject() {
    // given
    final var source = new POJO();
    source.setEnum(POJOEnum.BAR);
    source.setLong(42L);
    source.setInt(7);
    source.setString(new UnsafeBuffer("hello".getBytes()));
    source.nestedObject().setLong(99L);

    final var target = new POJO();

    // when
    target.copyFrom(source);

    // then
    assertThat(target.getEnum()).isEqualTo(POJOEnum.BAR);
    assertThat(target.getLong()).isEqualTo(42L);
    assertThat(target.getInt()).isEqualTo(7);
    assertThat(target.getString()).isEqualTo(new UnsafeBuffer("hello".getBytes()));
    assertThat(target.nestedObject().getLong()).isEqualTo(99L);
  }

  @Test
  void shouldBeIndependentAfterCopy() {
    // given
    final var source = new POJO();
    source.setLong(42L);
    source.setInt(7);
    source.setEnum(POJOEnum.FOO);
    source.setString(new UnsafeBuffer("hello".getBytes()));
    source.setBinary(new UnsafeBuffer(new byte[] {1}));
    source.setPacked(new UnsafeBuffer(new byte[] {0x01}));
    source.nestedObject().setLong(10L);

    final var target = new POJO();
    source.copyTo(target);

    // when — mutate source
    source.setLong(999L);
    source.setInt(888);
    source.setEnum(POJOEnum.BAR);
    source.nestedObject().setLong(777L);

    // then — target unchanged
    assertThat(target.getLong()).isEqualTo(42L);
    assertThat(target.getInt()).isEqualTo(7);
    assertThat(target.getEnum()).isEqualTo(POJOEnum.FOO);
    assertThat(target.nestedObject().getLong()).isEqualTo(10L);
  }

  @Test
  void shouldCopyBackAndForth() {
    // given
    final var a = new POJO();
    a.setLong(1L);
    a.setInt(2);
    a.setEnum(POJOEnum.FOO);
    a.setString(new UnsafeBuffer("a".getBytes()));
    a.setBinary(new UnsafeBuffer(new byte[] {1}));
    a.setPacked(new UnsafeBuffer(new byte[] {0x01}));
    a.nestedObject().setLong(3L);

    final var b = new POJO();
    b.setLong(10L);
    b.setInt(20);
    b.setEnum(POJOEnum.BAR);
    b.setString(new UnsafeBuffer("b".getBytes()));
    b.setBinary(new UnsafeBuffer(new byte[] {2}));
    b.setPacked(new UnsafeBuffer(new byte[] {0x02}));
    b.nestedObject().setLong(30L);

    // when — copy a→b then b→a
    a.copyTo(b);
    assertThat(b.getLong()).isEqualTo(1L);

    b.setLong(999L);
    b.copyTo(a);

    // then
    assertThat(a.getLong()).isEqualTo(999L);
    assertThat(a.getInt()).isEqualTo(2);
  }

  @Test
  void shouldCreateNewInstance() {
    // given
    final var source = new POJO();

    // when
    final var instance = source.createNewInstance();

    // then
    assertThat(instance).isInstanceOf(POJO.class);
    assertThat(instance).isNotSameAs(source);
  }

  @Test
  void shouldCopyToNewInstance() {
    // given
    final var source = new POJO();
    source.setLong(42L);
    source.setInt(7);
    source.setEnum(POJOEnum.BAR);
    source.setString(new UnsafeBuffer("test".getBytes()));
    source.setBinary(new UnsafeBuffer(new byte[] {5, 6}));
    source.setPacked(new UnsafeBuffer(new byte[] {0x07}));
    source.nestedObject().setLong(88L);

    // when
    final var target = (POJO) source.createNewInstance();
    source.copyTo(target);

    // then
    assertThat(target.getLong()).isEqualTo(42L);
    assertThat(target.getInt()).isEqualTo(7);
    assertThat(target.getEnum()).isEqualTo(POJOEnum.BAR);
    assertThat(target.nestedObject().getLong()).isEqualTo(88L);
  }

  @Test
  void shouldProduceIdenticalSerialization() {
    // given — a fully populated POJO
    final var source = new POJO();
    source.setEnum(POJOEnum.BAR);
    source.setLong(42L);
    source.setInt(7);
    source.setString(new UnsafeBuffer("hello".getBytes()));
    source.setBinary(new UnsafeBuffer(new byte[] {1, 2, 3}));
    source.setPacked(new UnsafeBuffer(new byte[] {0x04}));
    source.nestedObject().setLong(99L);

    final var target = new POJO();
    source.copyTo(target);

    // when — serialize both
    final var srcBuf = new UnsafeBuffer(new byte[source.getLength()]);
    source.write(srcBuf, 0);
    final var tgtBuf = new UnsafeBuffer(new byte[target.getLength()]);
    target.write(tgtBuf, 0);

    // then — identical bytes
    assertThat(tgtBuf).isEqualTo(srcBuf);
  }
}
