/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.camunda.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

public final class CamundaClientCredentialsTest {

  @Test
  void shouldBeValidWhenExpiryIsFarInTheFuture() {
    // given
    final ZonedDateTime farFuture = ZonedDateTime.of(3020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"));
    final CamundaClientCredentials credentials =
        new CamundaClientCredentials("token", farFuture, "Bearer");

    // when
    final boolean valid = credentials.isValid();

    // then
    assertThat(valid).isTrue();
  }

  @Test
  void shouldBeInvalidWhenTokenIsExpired() {
    // given
    final ZonedDateTime past = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"));
    final CamundaClientCredentials credentials =
        new CamundaClientCredentials("token", past, "Bearer");

    // when
    final boolean valid = credentials.isValid();

    // then
    assertThat(valid).isFalse();
  }

  @Test
  void shouldBeInvalidWhenTokenExpiresWithinGracePeriod() {
    // given — token that expires in 2 seconds (within the 5-second grace period)
    final ZonedDateTime expiresInTwoSeconds = ZonedDateTime.now().plusSeconds(2);
    final CamundaClientCredentials credentials =
        new CamundaClientCredentials("token", expiresInTwoSeconds, "Bearer");

    // when
    final boolean valid = credentials.isValid();

    // then — should be considered invalid because it's within the grace period
    assertThat(valid).isFalse();
  }

  @Test
  void shouldBeValidWhenTokenExpiresWellAfterGracePeriod() {
    // given — token that expires in 30 seconds (well beyond the 5-second grace period)
    final ZonedDateTime expiresInThirtySeconds = ZonedDateTime.now().plusSeconds(30);
    final CamundaClientCredentials credentials =
        new CamundaClientCredentials("token", expiresInThirtySeconds, "Bearer");

    // when
    final boolean valid = credentials.isValid();

    // then
    assertThat(valid).isTrue();
  }

  @Test
  void shouldRefreshProactivelyWhenTokenIsInSoftExpiryWindow() {
    // given — token expires in 45 seconds: within the 60s proactive window but comfortably
    // beyond the 5s grace period, so isValid()=true and shouldRefreshProactively()=true
    final ZonedDateTime expiresInFortyFiveSeconds = ZonedDateTime.now().plusSeconds(45);
    final CamundaClientCredentials credentials =
        new CamundaClientCredentials("token", expiresInFortyFiveSeconds, "Bearer");

    // when
    final boolean valid = credentials.isValid();
    final boolean shouldRefresh = credentials.shouldRefreshProactively();

    // then
    assertThat(valid).isTrue();
    assertThat(shouldRefresh).isTrue();
  }

  @Test
  void shouldNotRefreshProactivelyWhenTokenIsFarFromExpiry() {
    // given — token expires in 120 seconds: well beyond the 60s proactive window
    final ZonedDateTime expiresInTwoMinutes = ZonedDateTime.now().plusSeconds(120);
    final CamundaClientCredentials credentials =
        new CamundaClientCredentials("token", expiresInTwoMinutes, "Bearer");

    // when
    final boolean shouldRefresh = credentials.shouldRefreshProactively();

    // then
    assertThat(shouldRefresh).isFalse();
  }

  @Test
  void shouldRefreshProactivelyWhenTokenIsAlreadyWithinGracePeriod() {
    // given — token expires in 2 seconds: past the grace period (so isValid()=false) and also
    // inside the proactive window. shouldRefreshProactively() is independent of validity;
    // callers compose the two predicates, so here we expect both: not valid, and refresh due.
    final ZonedDateTime expiresInTwoSeconds = ZonedDateTime.now().plusSeconds(2);
    final CamundaClientCredentials credentials =
        new CamundaClientCredentials("token", expiresInTwoSeconds, "Bearer");

    // when
    final boolean valid = credentials.isValid();
    final boolean shouldRefresh = credentials.shouldRefreshProactively();

    // then
    assertThat(valid).isFalse();
    assertThat(shouldRefresh).isTrue();
  }
}
