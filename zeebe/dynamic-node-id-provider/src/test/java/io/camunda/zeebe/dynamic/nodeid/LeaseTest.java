/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static io.camunda.zeebe.dynamic.nodeid.Lease.OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.dynamic.nodeid.Lease.VersionMappings;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class LeaseTest {
  final NodeInstance nodeInstance = new NodeInstance(1, new Version(117L));
  final long originalTimestamp = 1000L;
  final Lease lease =
      new Lease(
          "task1",
          originalTimestamp,
          nodeInstance,
          new VersionMappings(Map.of(1, Version.of(117L), 2, Version.of(119L), 3, Version.of(3L))));

  @Test
  public void shouldReturnIfValid() {
    assertThat(lease.isStillValid(originalTimestamp)).isTrue();
    assertThat(lease.isStillValid(originalTimestamp + 1)).isFalse();
    assertThat(lease.isStillValid(originalTimestamp - 1)).isTrue();
    assertThat(lease.isStillValid(0)).isTrue();
  }

  @Test
  public void shouldRenewCorrectlyWhenValid() {
    // given
    final var currentTime = 500L;
    final var renewalDuration = Duration.ofSeconds(5);

    // when
    final var renewedLease = lease.renew(currentTime, renewalDuration, VersionMappings.empty());

    // then
    assertThat(renewedLease.taskId()).isEqualTo("task1");
    assertThat(renewedLease.timestamp()).isEqualTo(currentTime + renewalDuration.toMillis());
    assertThat(renewedLease.nodeInstance()).isEqualTo(nodeInstance);
  }

  @Test
  public void shouldNotRenewLeaseWhenExpired() {
    // given
    final var currentTime = 1120830000L;
    final var renewalDuration = Duration.ofSeconds(5);
    assertThat(lease.isStillValid(currentTime)).isFalse();

    // when/then
    final var expectedMessage =
        String.format(
            "Lease is not valid anymore(%s), it expired at %s",
            Instant.ofEpochMilli(currentTime), Instant.ofEpochMilli(lease.timestamp()));
    assertThatThrownBy(() -> lease.renew(currentTime, renewalDuration, VersionMappings.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(expectedMessage);
  }

  @Test
  public void shouldSerializeDeserializeToJson() {
    // when
    final var serialized = lease.toJson(OBJECT_MAPPER);

    // then
    final var expectedJson =
        """
        {"taskId":"task1","timestamp":1000,"nodeInstance":{"id":1,"version":117},"knownVersionMappings":{"mappingsByNodeId":{"1":117,"2":119,"3":3}}}""";
    assertThat(serialized).isEqualTo(expectedJson);
    final var deserialized = Lease.fromJson(OBJECT_MAPPER, serialized);
    assertThat(deserialized).isEqualTo(lease);
  }
}
