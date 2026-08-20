/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

/** Version test. */
public class VersionTest {

  @Test
  public void shouldAllowAlphaReleaseCandidates() {
    // given
    final var version = "8.6.0-alpha1-rc1";

    // when
    final Version from = Version.from(version);

    // then
    assertThat(from.major()).isEqualTo(8);
    assertThat(from.minor()).isEqualTo(6);
    assertThat(from.patch()).isEqualTo(0);
    assertThat(from.preRelease()).isEqualTo("alpha1-rc1");
    assertThat(from.buildMetadata()).isNull();
  }

  @Test
  public void testVersionComparison() {
    assertThat(Version.from("1.0.0")).isLessThan(Version.from("2.0.0"));
    assertThat(Version.from("2.0.0")).isGreaterThan(Version.from("1.0.0"));
    assertThat(Version.from("1.0.0")).isGreaterThan(Version.from("0.1.0"));
    assertThat(Version.from("0.1.0"))
        .isLessThan(Version.from("1.0.0"))
        .isLessThan(Version.from("0.1.1"));
    assertThat(Version.from("1.0.0")).isGreaterThan(Version.from("0.0.1"));
    assertThat(Version.from("1.1.1")).isGreaterThan(Version.from("1.0.3"));
    assertThat(Version.from("1.0.0")).isGreaterThan(Version.from("1.0.0-beta1"));
    assertThat(Version.from("1.0.0-rc2")).isGreaterThan(Version.from("1.0.0-rc1"));
    assertThat(Version.from("1.0.0-rc2.1")).isGreaterThan(Version.from("1.0.0-rc2"));
    assertThat(Version.from("1.0.0-rc2.1.1")).isGreaterThan(Version.from("1.0.0-rc2.1"));
    assertThat(Version.from("1.0.0-rc2")).isLessThan(Version.from("1.0.0-rc2.1"));
    assertThat(Version.from("1.0.0-rc1")).isGreaterThan(Version.from("1.0.0-beta1"));
    assertThat(Version.from("2.0.0-beta1")).isGreaterThan(Version.from("1.0.0"));
    assertThat(Version.from("1.0.0-alpha1")).isGreaterThan(Version.from("1.0.0-SNAPSHOT"));
    assertThat(Version.from("1.0.0-alpha1-rc1")).isLessThan(Version.from("1.0.0-alpha1-rc2"));
  }

  @Test
  public void testVersionToString() {
    assertThat(Version.from("1.0.0")).hasToString("1.0.0");
    assertThat(Version.from("1.0.0-alpha1")).hasToString("1.0.0-alpha1");
    assertThat(Version.from("1.0.0-beta1")).hasToString("1.0.0-beta1");
    assertThat(Version.from("1.0.0-rc1")).hasToString("1.0.0-rc1");
    assertThat(Version.from("1.0.0-rc1.2")).hasToString("1.0.0-rc1.2");
    assertThat(Version.from("1.0.0-SNAPSHOT")).hasToString("1.0.0-SNAPSHOT");
  }

  @Test
  public void testInvalidVersions() {
    assertThatThrownBy(() -> Version.from("1")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Version.from("1.0")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Version.from("1.0-beta1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Version.from("1.0.0.0")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Version.from("1.0.0.0-beta1"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
