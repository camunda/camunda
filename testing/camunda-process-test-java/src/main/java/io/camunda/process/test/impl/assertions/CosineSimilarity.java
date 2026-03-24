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

/** Utility class for computing the cosine similarity between two embedding vectors. */
final class CosineSimilarity {

  private CosineSimilarity() {}

  /**
   * Computes the cosine similarity between two float vectors.
   *
   * <p>Returns {@code 0.0} if either vector is a zero vector. The result is clamped to {@code [0.0,
   * 1.0]}.
   *
   * @param a the first embedding vector
   * @param b the second embedding vector
   * @return the cosine similarity in the range {@code [0.0, 1.0]}
   */
  static double compute(final float[] a, final float[] b) {
    if (a.length != b.length) {
      throw new IllegalArgumentException(
          "Embedding vectors must have the same length, but got a.length="
              + a.length
              + " and b.length="
              + b.length
              + ". Ensure both texts are embedded with the same model and dimensions.");
    }
    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }
    if (normA == 0.0 || normB == 0.0) {
      return 0.0;
    }
    return Math.min(1.0, Math.max(0.0, dot / (Math.sqrt(normA) * Math.sqrt(normB))));
  }
}
