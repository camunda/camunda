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
package io.camunda.process.test.api.similarity;

import io.camunda.process.test.impl.similarity.SemanticSimilarityConfigImpl;
import java.util.List;

/**
 * Configuration for the embedding model used in semantic similarity assertions.
 *
 * <p>Example usage:
 *
 * <pre>
 *   SemanticSimilarityConfig config = SemanticSimilarityConfig.of(myEmbeddingModel::embed)
 *       .withThreshold(0.85);
 *   CamundaAssert.setSemanticSimilarityConfig(config);
 * </pre>
 *
 * <p>By default, three preprocessors are applied to both the expected and actual text before
 * computing embeddings, in this order: Unicode normalization → whitespace normalization →
 * lowercasing, see {@link TextPreprocessors#defaults()}. Use {@link #withoutPreprocessors()} to
 * disable all preprocessing, or {@link #withPreprocessors(TextPreprocessor...)} to supply a custom
 * pipeline built by chaining preprocessors from {@link TextPreprocessors}.
 */
public interface SemanticSimilarityConfig {

  /** The default threshold score (0-1) above which a semantic similarity assertion passes. */
  double DEFAULT_THRESHOLD = 0.5;

  /**
   * Creates a new SemanticSimilarityConfig with default settings and no embedding model. An
   * embedding model must be set via {@link #withEmbeddingModelAdapter(EmbeddingModelAdapter)}
   * before using the config for similarity evaluations.
   *
   * @return a new SemanticSimilarityConfig instance with default settings
   */
  static SemanticSimilarityConfig defaults() {
    return new SemanticSimilarityConfigImpl(null, DEFAULT_THRESHOLD, TextPreprocessors.defaults());
  }

  /**
   * Creates a new SemanticSimilarityConfig with the given embedding model, default threshold, and
   * all default preprocessors enabled (Unicode normalization → whitespace normalization →
   * lowercasing, see {@link TextPreprocessors#defaults()}).
   *
   * @param embeddingModel the embedding model adapter to use for similarity evaluations
   * @return a new SemanticSimilarityConfig instance
   */
  static SemanticSimilarityConfig of(final EmbeddingModelAdapter embeddingModel) {
    if (embeddingModel == null) {
      throw new IllegalArgumentException("embeddingModel must not be null");
    }
    return new SemanticSimilarityConfigImpl(
        embeddingModel, DEFAULT_THRESHOLD, TextPreprocessors.defaults());
  }

  /**
   * Creates a new SemanticSimilarityConfig with the given embedding model, threshold, and all
   * default preprocessors enabled (Unicode → whitespace → lowercase, see {@link
   * TextPreprocessors#defaults()}).
   *
   * @param embeddingModel the embedding model adapter to use for similarity evaluations
   * @param threshold the threshold score (0-1) above which a similarity assertion passes
   * @return a new SemanticSimilarityConfig instance
   */
  static SemanticSimilarityConfig of(
      final EmbeddingModelAdapter embeddingModel, final double threshold) {
    return of(embeddingModel).withThreshold(threshold);
  }

  /**
   * Returns a new SemanticSimilarityConfig with the given embedding model adapter, keeping all
   * other settings.
   *
   * @param embeddingModel the embedding model adapter to use for similarity evaluations
   * @return a new SemanticSimilarityConfig instance with the updated embedding model
   */
  SemanticSimilarityConfig withEmbeddingModelAdapter(EmbeddingModelAdapter embeddingModel);

  /**
   * Returns a new SemanticSimilarityConfig with the given threshold, keeping all other settings.
   *
   * @param threshold the threshold score (0-1) above which a similarity assertion passes
   * @return a new SemanticSimilarityConfig instance with the updated threshold
   */
  SemanticSimilarityConfig withThreshold(double threshold);

  /**
   * Returns a new SemanticSimilarityConfig with the given preprocessors replacing the current
   * pipeline, keeping all other settings. Preprocessors are applied in the order provided.
   *
   * @param preprocessors the preprocessors to apply in order before computing embeddings
   * @return a new SemanticSimilarityConfig instance with the updated preprocessors
   */
  SemanticSimilarityConfig withPreprocessors(TextPreprocessor... preprocessors);

  /**
   * Returns a new SemanticSimilarityConfig with preprocessing disabled (empty pipeline), keeping
   * all other settings.
   *
   * @return a new SemanticSimilarityConfig instance with no preprocessors
   */
  SemanticSimilarityConfig withoutPreprocessors();

  /**
   * Returns the embedding model adapter, or {@code null} if not yet configured.
   *
   * @return the embedding model adapter, or {@code null}
   */
  EmbeddingModelAdapter getEmbeddingModel();

  /**
   * Returns the threshold score.
   *
   * @return the threshold score (0-1)
   */
  double getThreshold();

  /**
   * Returns the list of text preprocessors applied before embedding. The list is unmodifiable.
   *
   * @return an unmodifiable list of text preprocessors
   */
  List<TextPreprocessor> getPreprocessors();
}
