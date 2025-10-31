/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class LeaseTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  final NodeInstance nodeInstance = new NodeInstance(1);
  final long originalTimestamp = 1000L;
  final Lease lease = new Lease("task1", originalTimestamp, nodeInstance);

  @Test
  public void shouldRenewCorrectlyWhenValid() {
    // given
    final var currentTime = 2000L;
    final var renewalDuration = Duration.ofSeconds(5);

    // when
    final var renewedLease = lease.renew(currentTime, renewalDuration);

    // then
    assertThat(renewedLease.taskId()).isEqualTo("task1");
    assertThat(renewedLease.timestamp()).isEqualTo(currentTime + renewalDuration.toMillis());
    assertThat(renewedLease.nodeInstance()).isEqualTo(nodeInstance);
  }

  @Test
  public void shouldNotRenewLeaseWhenExpired() {
    // given
    final var currentTime = 10000L;
    final var renewalDuration = Duration.ofSeconds(5);
    assertThat(lease.isStillValid(currentTime, renewalDuration)).isFalse();

    // when/then
    assertThatThrownBy(() -> lease.renew(currentTime, renewalDuration))
        .isInstanceOf(IllegalStateException.class)
        .withFailMessage("Lease is not valid anymore, it expired at");
  }

  @Test
  public void shouldSerializeDeserializeToJson() {
    // when
    final var serialized = lease.toJson(OBJECT_MAPPER);

    // then
    final var deserialized = Lease.fromJson(OBJECT_MAPPER, serialized);
    assertThat(deserialized).isEqualTo(lease);
  }
}
