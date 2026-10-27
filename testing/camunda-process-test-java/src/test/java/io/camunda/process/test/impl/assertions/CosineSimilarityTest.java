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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class CosineSimilarityTest {

  @Test
  void shouldReturnOneForIdenticalVectors() {
    final float[] v = {1.0f, 0.0f};
    assertThat(CosineSimilarity.compute(v, v)).isEqualTo(1.0);
  }

  @Test
  void shouldReturnZeroForOrthogonalVectors() {
    final float[] x = {1.0f, 0.0f};
    final float[] y = {0.0f, 1.0f};
    assertThat(CosineSimilarity.compute(x, y)).isEqualTo(0.0);
  }

  @Test
  void shouldReturnZeroForZeroVector() {
    final float[] zero = {0.0f, 0.0f};
    final float[] v = {1.0f, 0.0f};
    assertThat(CosineSimilarity.compute(zero, v)).isEqualTo(0.0);
    assertThat(CosineSimilarity.compute(v, zero)).isEqualTo(0.0);
  }

  @Test
  void shouldReturnZeroForBothZeroVectors() {
    final float[] zero = {0.0f, 0.0f};
    assertThat(CosineSimilarity.compute(zero, zero)).isEqualTo(0.0);
  }

  @Test
  void shouldComputeKnownSimilarity() {
    // [1,0] · [4,3] / (1 * 5) = 4/5 = 0.8
    final float[] a = {1.0f, 0.0f};
    final float[] b = {4.0f, 3.0f};
    assertThat(CosineSimilarity.compute(a, b)).isCloseTo(0.8, within(1e-9));
  }

  @Test
  void shouldBeSymmetric() {
    final float[] a = {1.0f, 2.0f, 3.0f};
    final float[] b = {4.0f, 5.0f, 6.0f};
    assertThat(CosineSimilarity.compute(a, b)).isEqualTo(CosineSimilarity.compute(b, a));
  }

  @Test
  void shouldClampToOneForNumericalNoise() {
    // Vectors that are nearly identical but floating-point arithmetic could yield > 1.0
    final float[] a = {1.0f, 1e-7f};
    final float[] b = {1.0f, 1e-7f};
    assertThat(CosineSimilarity.compute(a, b)).isLessThanOrEqualTo(1.0);
  }

  @Test
  void shouldThrowForMismatchedVectorLengths() {
    final float[] a = {1.0f, 2.0f, 3.0f};
    final float[] b = {4.0f, 5.0f};
    assertThatThrownBy(() -> CosineSimilarity.compute(a, b))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a.length=3")
        .hasMessageContaining("b.length=2");
  }

  @Test
  void shouldHandleHighDimensionalVectors() {
    final int dims = 1536;
    final float[] a = new float[dims];
    final float[] b = new float[dims];
    for (int i = 0; i < dims; i++) {
      a[i] = 1.0f / dims;
      b[i] = 1.0f / dims;
    }
    assertThat(CosineSimilarity.compute(a, b)).isCloseTo(1.0, within(1e-6));
  }
}
