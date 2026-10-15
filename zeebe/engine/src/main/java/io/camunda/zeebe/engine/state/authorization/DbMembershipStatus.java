/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.MembershipStatus;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * The value of a membership row. Membership rows used to store {@link DbNil}, which writes the
 * single byte {@code 0xFF}. This value writes the same byte for {@link MembershipStatus#ACTIVE}, so
 * existing rows read as active and an active row stays byte-identical to the unversioned layout.
 * Only {@link MembershipStatus#REMOVED}, which a policy update writes at a version of 1 or more,
 * uses a new byte.
 */
final class DbMembershipStatus implements DbValue {

  /** The byte {@link DbNil} writes. */
  private static final byte ACTIVE_BYTE = (byte) 0xFF;

  private static final byte REMOVED_BYTE = (byte) 0x00;

  private MembershipStatus status = MembershipStatus.ACTIVE;

  MembershipStatus getValue() {
    return status;
  }

  void setValue(final MembershipStatus status) {
    this.status = status;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    final byte value = buffer.getByte(offset);
    status =
        switch (value) {
          case ACTIVE_BYTE -> MembershipStatus.ACTIVE;
          case REMOVED_BYTE -> MembershipStatus.REMOVED;
          default ->
              throw new IllegalStateException(
                  "Expected a membership status byte of 0xFF or 0x00, but found 0x%02X"
                      .formatted(value));
        };
  }

  @Override
  public int getLength() {
    return Byte.BYTES;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putByte(offset, status == MembershipStatus.ACTIVE ? ACTIVE_BYTE : REMOVED_BYTE);
    return Byte.BYTES;
  }

  @Override
  public String toString() {
    return "DbMembershipStatus{" + status + '}';
  }
}
