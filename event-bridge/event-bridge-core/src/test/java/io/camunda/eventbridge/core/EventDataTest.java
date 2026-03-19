/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class EventDataTest {

  @Test
  void shouldRejectNullBody() {
    // given / when / then
    assertThatNullPointerException()
        .isThrownBy(() -> new EventData(null))
        .withMessage("body must not be null");
  }

  @Test
  void shouldWrapBodyByReference() {
    // given
    final byte[] payload = {1, 2, 3};

    // when
    final var event = new EventData(payload);

    // then
    assertThat(event.body()).isSameAs(payload);
  }

  @Test
  void shouldReportSizeInBytes() {
    // given
    final byte[] payload = {10, 20, 30, 40};

    // when
    final var event = new EventData(payload);

    // then
    assertThat(event.sizeInBytes()).isEqualTo(4);
  }

  @Test
  void shouldAcceptZeroLengthBody() {
    // given
    final byte[] empty = new byte[0];

    // when
    final var event = new EventData(empty);

    // then
    assertThat(event.sizeInBytes()).isZero();
    assertThat(event.body()).isEmpty();
  }

  @Test
  void shouldReflectMutationOfBackingArray() {
    // given — caller owns the backing array; no defensive copy is made
    final byte[] payload = {1, 2, 3};
    final var event = new EventData(payload);

    // when
    payload[0] = 99;

    // then — mutation is visible through body() because no copy was taken
    assertThat(event.body()[0]).isEqualTo((byte) 99);
  }

  @Test
  void shouldKeepSizeInBytesSyncWithBodyLength() {
    // given
    final byte[] payload = new byte[256];

    // when
    final var event = new EventData(payload);

    // then
    assertThat(event.sizeInBytes()).isEqualTo(event.body().length).isEqualTo(256);
  }

  @Test
  void shouldUseReferenceEqualityForArraysInEquals() {
    // given — two separate arrays with identical content
    final byte[] a = {1, 2, 3};
    final byte[] b = {1, 2, 3};
    final var eventA = new EventData(a);
    final var eventB = new EventData(b);
    final var eventASame = new EventData(a);

    // then — Java records use Objects.equals for components; for arrays that is reference equality
    assertThat(eventA).isNotEqualTo(eventB);
    assertThat(eventA).isEqualTo(eventASame);
  }

  @Test
  void shouldUseReferenceEqualityForHashCode() {
    // given
    final byte[] a = {1, 2, 3};
    final byte[] b = {1, 2, 3};

    // when
    final var eventA = new EventData(a);
    final var eventASame = new EventData(a);
    final var eventB = new EventData(b);

    // then — same backing array → same hashCode (Java contract: equal objects must have equal
    // hashCode)
    assertThat(eventA.hashCode()).isEqualTo(eventASame.hashCode());
  }
}
