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
package io.camunda.client.impl.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.BackpressureProfile;
import org.junit.jupiter.api.Test;

final class BackpressureSettingsTest {

  @Test
  void shouldNotHandleBackpressureWithoutAProfile() {
    // when
    final BackpressureSettings settings = BackpressureSettings.forProfile(null);

    // then
    assertThat(settings.isEnabled()).isFalse();
    assertThat(settings.isGating()).isFalse();
  }

  @Test
  void shouldNotHandleBackpressureWhenDisabled() {
    final BackpressureSettings settings =
        BackpressureSettings.forProfile(BackpressureProfile.DISABLED);

    assertThat(settings.isEnabled()).isFalse();
    assertThat(settings.isGating()).isFalse();
  }

  @Test
  void shouldRetryAndLimitWithTheOtherProfiles() {
    for (final BackpressureProfile profile :
        new BackpressureProfile[] {
          BackpressureProfile.BALANCED,
          BackpressureProfile.CONSERVATIVE,
          BackpressureProfile.AGGRESSIVE
        }) {
      final BackpressureSettings settings = BackpressureSettings.forProfile(profile);
      assertThat(settings.isEnabled()).describedAs(profile.name()).isTrue();
      assertThat(settings.isGating()).describedAs(profile.name()).isTrue();
      assertThat(settings.maxAttempts()).describedAs(profile.name()).isEqualTo(3);
    }
  }

  @Test
  void shouldRetryButNotLimitWithTheLegacyProfile() {
    final BackpressureSettings settings =
        BackpressureSettings.forProfile(BackpressureProfile.LEGACY);

    assertThat(settings.isEnabled()).isTrue();
    assertThat(settings.isGating()).isFalse();
  }
}
