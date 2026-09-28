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
package io.camunda.process.test.api.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class WeightedExpectationTest {

  @Test
  void shouldUseDefaultWeightWhenNotSpecified() {
    // when
    final WeightedExpectation expectation = WeightedExpectation.of("should be polite");

    // then
    assertThat(expectation.getCriterion()).isEqualTo("should be polite");
    assertThat(expectation.getWeight()).isEqualTo(1.0);
  }

  @Test
  void shouldUseExplicitWeight() {
    // when
    final WeightedExpectation expectation = WeightedExpectation.of("should mention a refund", 2.5);

    // then
    assertThat(expectation.getCriterion()).isEqualTo("should mention a refund");
    assertThat(expectation.getWeight()).isEqualTo(2.5);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void shouldRejectNullOrBlankCriterion(final String criterion) {
    assertThatThrownBy(() -> WeightedExpectation.of(criterion))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("criterion must not be null or empty");
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY})
  void shouldRejectNonPositiveOrNonFiniteWeight(final double weight) {
    assertThatThrownBy(() -> WeightedExpectation.of("some criterion", weight))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("weight must be a positive, finite number");
  }
}
