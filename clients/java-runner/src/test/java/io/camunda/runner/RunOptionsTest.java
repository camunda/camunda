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
package io.camunda.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RunOptionsTest {

  @Test
  void shouldRejectNegativeInstances() {
    // expect
    assertThatThrownBy(() -> RunOptions.of(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldDefaultPacingToNullAndTimeoutToFiveMinutes() {
    // given
    final RunOptions opts = RunOptions.of(3);

    // expect
    assertThat(opts.instances()).isEqualTo(3);
    assertThat(opts.pacingOrNull()).isNull();
    assertThat(opts.timeout()).isEqualTo(Duration.ofMinutes(5));
    assertThat(opts.extraTags()).isEmpty();
    assertThat(opts.variablesFor(0)).isEmpty();
  }

  @Test
  void shouldStoreAndReturnPacing() {
    // given / when
    final RunOptions opts = RunOptions.of(5).pacing(Duration.ofMillis(50));

    // then
    assertThat(opts.pacingOrNull()).isEqualTo(Duration.ofMillis(50));
  }

  @Test
  void shouldAcceptNullPacingForEager() {
    // given / when
    final RunOptions opts = RunOptions.of(2).pacing(Duration.ofSeconds(1)).pacing(null);

    // then
    assertThat(opts.pacingOrNull()).isNull();
  }

  @Test
  void shouldRejectNullTimeout() {
    // expect
    assertThatThrownBy(() -> RunOptions.of(1).timeout(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAddExtraTags() {
    // given / when
    final RunOptions opts = RunOptions.of(1).tags("env:dev", "team:flow");

    // then
    assertThat(opts.extraTags()).containsExactly("env:dev", "team:flow");
  }

  @Test
  void shouldReturnConstantVariablesForEveryIndex() {
    // given
    final RunOptions opts = RunOptions.of(3).variables(Map.of("orderId", "O-1", "amount", 19.99));

    // expect
    assertThat(opts.variablesFor(0)).containsEntry("orderId", "O-1").containsEntry("amount", 19.99);
    assertThat(opts.variablesFor(2)).isEqualTo(opts.variablesFor(0));
  }

  @Test
  void shouldReturnGeneratedVariablesPerIndex() {
    // given
    final RunOptions opts =
        RunOptions.of(3).variables(i -> Map.of("orderId", "ORDER-" + (1000 + i)));

    // expect
    assertThat(opts.variablesFor(0)).containsEntry("orderId", "ORDER-1000");
    assertThat(opts.variablesFor(1)).containsEntry("orderId", "ORDER-1001");
    assertThat(opts.variablesFor(2)).containsEntry("orderId", "ORDER-1002");
  }

  @Test
  void shouldHaveLastWriteWinsForVariables() {
    // given — generator first, then constant map: constant wins
    final RunOptions a = RunOptions.of(2).variables(i -> Map.of("g", i)).variables(Map.of("c", 1));

    // and — constant first, then generator: generator wins
    final RunOptions b = RunOptions.of(2).variables(Map.of("c", 1)).variables(i -> Map.of("g", i));

    // expect
    assertThat(a.variablesFor(0)).containsOnlyKeys("c");
    assertThat(b.variablesFor(0)).containsOnlyKeys("g");
  }

  @Test
  void shouldTreatNullGeneratorOutputAsEmpty() {
    // given
    final RunOptions opts = RunOptions.of(1).variables(i -> null);

    // expect
    assertThat(opts.variablesFor(0)).isEmpty();
  }
}
