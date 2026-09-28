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

import org.apache.commons.lang3.StringUtils;

/**
 * A single natural language expectation with a relative weight, for evaluating several criteria
 * against one actual value in a single judge call. Requires a {@link
 * BatchExpectationChatModelAdapter}.
 *
 * <p>Weights are relative importance, not required to sum to 1: the overall score is the weighted
 * average {@code sum(weight * truthValue) / sum(weight)}.
 */
public final class WeightedExpectation {

  public static final double DEFAULT_WEIGHT = 1.0;

  private final String criterion;
  private final double weight;

  private WeightedExpectation(final String criterion, final double weight) {
    if (StringUtils.isBlank(criterion)) {
      throw new IllegalArgumentException("criterion must not be null or empty");
    }
    if (!Double.isFinite(weight) || weight <= 0.0) {
      throw new IllegalArgumentException(
          "weight must be a positive, finite number, was: " + weight);
    }
    this.criterion = criterion;
    this.weight = weight;
  }

  /** Creates an expectation with the default weight ({@value #DEFAULT_WEIGHT}). */
  public static WeightedExpectation of(final String criterion) {
    return new WeightedExpectation(criterion, DEFAULT_WEIGHT);
  }

  /** Creates an expectation with an explicit relative weight (must be positive and finite). */
  public static WeightedExpectation of(final String criterion, final double weight) {
    return new WeightedExpectation(criterion, weight);
  }

  public String getCriterion() {
    return criterion;
  }

  public double getWeight() {
    return weight;
  }

  @Override
  public String toString() {
    return String.format("WeightedExpectation{criterion='%s', weight=%.2f}", criterion, weight);
  }
}
