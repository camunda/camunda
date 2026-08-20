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
package io.camunda.process.test.impl.similarity;

import io.camunda.process.test.api.similarity.EmbeddingModelAdapter;
import io.camunda.process.test.api.similarity.SemanticSimilarityConfig;
import io.camunda.process.test.api.similarity.TextPreprocessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SemanticSimilarityConfigImpl implements SemanticSimilarityConfig {

  private final EmbeddingModelAdapter embeddingModel;
  private final double threshold;
  private final List<TextPreprocessor> preprocessors;

  public SemanticSimilarityConfigImpl(
      final EmbeddingModelAdapter embeddingModel,
      final double threshold,
      final List<TextPreprocessor> preprocessors) {
    this.embeddingModel = embeddingModel;
    this.threshold = threshold;
    this.preprocessors =
        preprocessors == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(preprocessors));
  }

  @Override
  public SemanticSimilarityConfig withEmbeddingModelAdapter(
      final EmbeddingModelAdapter embeddingModel) {
    if (embeddingModel == null) {
      throw new IllegalArgumentException("embeddingModel must not be null");
    }
    return new SemanticSimilarityConfigImpl(embeddingModel, threshold, preprocessors);
  }

  @Override
  public SemanticSimilarityConfig withThreshold(final double threshold) {
    if (threshold < 0.0 || threshold > 1.0) {
      throw new IllegalArgumentException(
          "threshold must be between 0.0 and 1.0, was: " + threshold);
    }
    return new SemanticSimilarityConfigImpl(embeddingModel, threshold, preprocessors);
  }

  @Override
  public SemanticSimilarityConfig withPreprocessors(final TextPreprocessor... preprocessors) {
    if (preprocessors == null) {
      throw new IllegalArgumentException("preprocessors must not be null");
    }
    return new SemanticSimilarityConfigImpl(
        embeddingModel, threshold, Arrays.asList(preprocessors));
  }

  @Override
  public SemanticSimilarityConfig withoutPreprocessors() {
    return new SemanticSimilarityConfigImpl(embeddingModel, threshold, Collections.emptyList());
  }

  @Override
  public EmbeddingModelAdapter getEmbeddingModel() {
    return embeddingModel;
  }

  @Override
  public double getThreshold() {
    return threshold;
  }

  @Override
  public List<TextPreprocessor> getPreprocessors() {
    return preprocessors;
  }
}
