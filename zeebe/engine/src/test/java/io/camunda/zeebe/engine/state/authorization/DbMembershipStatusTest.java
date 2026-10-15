/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.MembershipStatus;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Membership rows written before policy versioning store {@link DbNil}. These tests pin the
 * encoding that keeps those rows readable without a migration.
 */
final class DbMembershipStatusTest {

  @Test
  void shouldReadDbNilAsActive() {
    // given
    final var buffer = new ExpandableArrayBuffer();
    final int length = DbNil.INSTANCE.write(buffer, 0);
    final var status = new DbMembershipStatus();
    status.setValue(MembershipStatus.REMOVED);

    // when
    status.wrap(buffer, 0, length);

    // then
    assertThat(status.getValue()).isEqualTo(MembershipStatus.ACTIVE);
  }

  @Test
  void shouldWriteActiveIdenticalToDbNil() {
    // given
    final var status = new DbMembershipStatus();
    status.setValue(MembershipStatus.ACTIVE);

    // when
    final var written = bytes(status::write, status.getLength());

    // then
    assertThat(written).isEqualTo(bytes(DbNil.INSTANCE::write, DbNil.INSTANCE.getLength()));
  }

  @Test
  void shouldRoundTripRemoved() {
    // given
    final var status = new DbMembershipStatus();
    status.setValue(MembershipStatus.REMOVED);
    final var written = bytes(status::write, status.getLength());

    // when
    final var read = new DbMembershipStatus();
    read.wrap(new UnsafeBuffer(written), 0, written.length);

    // then
    assertThat(read.getValue()).isEqualTo(MembershipStatus.REMOVED);
  }

  @Test
  void shouldRejectUnknownByte() {
    // given
    final var buffer = new UnsafeBuffer(new byte[] {(byte) 0x80});
    final var status = new DbMembershipStatus();

    // when / then
    assertThatThrownBy(() -> status.wrap(buffer, 0, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("0x80");
  }

  private static byte[] bytes(final Writer writer, final int length) {
    final var buffer = new ExpandableArrayBuffer();
    writer.write(buffer, 0);
    final var result = new byte[length];
    buffer.getBytes(0, result);
    return result;
  }

  @FunctionalInterface
  private interface Writer {
    int write(ExpandableArrayBuffer buffer, int offset);
  }
}
