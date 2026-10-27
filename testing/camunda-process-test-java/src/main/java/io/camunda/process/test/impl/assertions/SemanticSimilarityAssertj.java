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

import static org.assertj.core.api.Assertions.fail;

import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reusable component that provides semantic similarity evaluation capabilities. Intended to be
 * composed into assertion classes that need embedding-based evaluation.
 */
public class SemanticSimilarityAssertj {

  private static final Logger LOG = LoggerFactory.getLogger(SemanticSimilarityAssertj.class);

  private SemanticSimilarityConfig config;

  public SemanticSimilarityAssertj(final SemanticSimilarityConfig config) {
    this.config = config;
  }

  SemanticSimilarityConfig getSemanticSimilarityConfig() {
    return config;
  }

  /**
   * Overrides the {@link SemanticSimilarityConfig}. The modifier receives the current config and
   * returns a new one.
   *
   * @param modifier function that transforms the current config
   */
  public void withSemanticSimilarityConfig(final UnaryOperator<SemanticSimilarityConfig> modifier) {
    if (modifier == null) {
      throw new IllegalArgumentException("modifier must not be null");
    }
    final SemanticSimilarityConfig base =
        config != null ? config : SemanticSimilarityConfig.defaults();
    final SemanticSimilarityConfig modified = modifier.apply(base);
    if (modified == null) {
      throw new IllegalArgumentException("modifier must not return null");
    }
    config = modified;
  }

  /**
   * Validates that the config has all required settings for evaluation.
   *
   * @throws IllegalStateException if the config or its embedding model is null
   */
  public void assertSimilarityHasAllRequiredSettings() {
    if (config == null) {
      throw new IllegalStateException(
          "SemanticSimilarityConfig is not set. Ensure to provide a SemanticSimilarityConfig instance to use similarity assertions.");
    }
    if (config.getEmbeddingModel() == null) {
      throw new IllegalStateException(
          "SemanticSimilarityConfig has no EmbeddingModelAdapter configured. "
              + "Use SemanticSimilarityConfig.of(embeddingModel) or withSemanticSimilarityConfig(config -> config.withEmbeddingModelAdapter(embeddingModel)).");
    }
  }

  /**
   * Evaluates the actual value against the expected value using text embeddings.
   *
   * @param expectedValue the expected text
   * @param actualValue the actual text to compare
   * @param subject the subject of the assertion prepended to the failure message (e.g. {@code
   *     "Value "} for standalone, or {@code "Process instance [1] variable 'myVar' "} for variable
   *     assertions), used to produce contextual failure messages
   */
  public void evaluateSimilarity(
      final String expectedValue, final String actualValue, final String subject) {
    final SimilarityEvaluation evaluation =
        new SimilarityEvaluation(
            config.getEmbeddingModel(), config.getPreprocessors(), expectedValue);
    final SimilarityEvaluation.Result result = evaluation.evaluate(actualValue);
    LOG.debug("Computed similarity score for {}: {}", subject.trim(), result.getScore());
    final double threshold = config.getThreshold();
    if (!result.passed(threshold)) {
      fail(
          "%sdid not satisfy similarity check.\n"
              + "  Expectation: %s\n"
              + "  Actual value: %s\n"
              + "  Score: %.2f (threshold: %.2f)\n",
          subject, expectedValue, actualValue, result.getScore(), threshold);
    }
  }
}
