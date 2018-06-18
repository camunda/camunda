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
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.Test;

public class DurationUtilTest {
  @Test
  public void shouldParseMilliseconds() {
    Duration duration = DurationUtil.parse("2ms");
    assertThat(duration).isEqualTo(Duration.ofMillis(2));

    duration = DurationUtil.parse("4.5ms");
    assertThat(duration).isEqualTo(Duration.of(4500, ChronoUnit.MICROS));
  }

  @Test
  public void shouldParseSeconds() {
    Duration duration = DurationUtil.parse("3s");
    assertThat(duration).isEqualTo(Duration.ofSeconds(3));

    duration = DurationUtil.parse("2.5s");
    assertThat(duration).isEqualTo(Duration.ofMillis(2500));
  }

  @Test
  public void shouldParseMinutes() {
    Duration duration = DurationUtil.parse("7m");
    assertThat(duration).isEqualTo(Duration.ofMinutes(7));

    duration = DurationUtil.parse("1.5m");
    assertThat(duration).isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  public void shouldParseHours() {
    Duration duration = DurationUtil.parse("1h");
    assertThat(duration).isEqualTo(Duration.ofHours(1));

    duration = DurationUtil.parse("5.25h");
    assertThat(duration).isEqualTo(Duration.ofMinutes(315));
  }

  @Test
  public void shouldDefaultToMillisWithoutUnit() {
    assertThat(DurationUtil.parse("12")).isEqualTo(Duration.ofMillis(12));
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldFailWithUnsupportedUnit() {
    DurationUtil.parse("30d");
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldFailWithNoValue() {
    DurationUtil.parse("m");
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldFailWithEmptyString() {
    DurationUtil.parse("");
  }

  @Test(expected = NumberFormatException.class)
  public void shouldFailWithNonParsableValue() {
    DurationUtil.parse(".,ms");
  }
}
