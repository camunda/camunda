/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class RoleRecordPolicyVersionTest {

  @Test
  void shouldReadIdentityRecordsWithoutPolicyVersionAsUnversioned() {
    // given: a payload written before the policyVersion property existed
    final var buffer = new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"roleId\":\"r\"}"));

    // when
    final var record = new RoleRecord();
    record.wrap(buffer);

    // then
    assertThat(record.getRoleId()).isEqualTo("r");
    assertThat(record.getPolicyVersion()).isZero();
  }

  @Test
  void shouldRoundTripPolicyVersionOnIdentityRecords() {
    // given
    final var original = new RoleRecord().setRoleKey(9L).setRoleId("r").setPolicyVersion(5L);

    // when
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);
    final var copy = new RoleRecord();
    copy.wrap(buffer);

    // then
    assertThat(copy.getPolicyVersion()).isEqualTo(5L);
  }
}
