/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RebalanceConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class LeadershipTransferInitiateRequestTest {

  private static final RebalanceConfiguration CONFIGURED =
      new RebalanceConfiguration(8 * 1024 * 1024, Duration.ofSeconds(10), 3);

  @Test
  void shouldKeepTheConfiguredSettingsWhenTheCoordinatorOverridesNothing() {
    // given
    final var request = requestBuilder().build();

    // when
    final var effective = request.effectiveConfiguration(CONFIGURED);

    // then
    assertThat(effective).isEqualTo(CONFIGURED);
  }

  @Test
  void shouldApplyEveryOverrideTheCoordinatorSent() {
    // given
    final var request =
        requestBuilder()
            .withReplicationLagThreshold(4096)
            .withReplicationTimeout(Duration.ofSeconds(30))
            .withMaxTransferAttempts(7)
            .build();

    // when
    final var effective = request.effectiveConfiguration(CONFIGURED);

    // then
    assertThat(effective).isEqualTo(new RebalanceConfiguration(4096, Duration.ofSeconds(30), 7));
  }

  @Test
  void shouldKeepTheConfiguredSettingsTheCoordinatorLeftUnset() {
    // given
    final var request = requestBuilder().withMaxTransferAttempts(7).build();

    // when
    final var effective = request.effectiveConfiguration(CONFIGURED);

    // then
    assertThat(effective)
        .isEqualTo(
            new RebalanceConfiguration(
                CONFIGURED.replicationLagThreshold(), CONFIGURED.replicationTimeout(), 7));
  }

  @Test
  void shouldRejectANegativeReplicationLagThresholdOverride() {
    // given / when / then
    assertThatThrownBy(() -> requestBuilder().withReplicationLagThreshold(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectANonPositiveReplicationTimeoutOverride() {
    // given / when / then
    assertThatThrownBy(() -> requestBuilder().withReplicationTimeout(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> requestBuilder().withReplicationTimeout(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectANonPositiveMaxTransferAttemptsOverride() {
    // given / when / then
    assertThatThrownBy(() -> requestBuilder().withMaxTransferAttempts(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static LeadershipTransferInitiateRequest.Builder requestBuilder() {
    return LeadershipTransferInitiateRequest.builder()
        .withDesiredLeader(MemberId.from("2"))
        .withCoordinator(MemberId.from("1"))
        .withCoordinatorConfigVersion(4)
        .withCorrelationId(0x5eed_0b01L);
  }
}
