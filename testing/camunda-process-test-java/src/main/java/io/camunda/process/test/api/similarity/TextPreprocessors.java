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

import io.camunda.process.test.impl.similarity.preprocessors.LowercaseNormalizerPreprocessor;
import io.camunda.process.test.impl.similarity.preprocessors.UnicodeNormalizerPreprocessor;
import io.camunda.process.test.impl.similarity.preprocessors.WhitespaceNormalizerPreprocessor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Factory for built-in {@link TextPreprocessor} implementations.
 *
 * <p>Preprocessors can be instantiated directly and chained via {@link
 * TextPreprocessor#andThen(TextPreprocessor)}:
 *
 * <pre>
 *   TextPreprocessor pipeline = TextPreprocessors.unicodeNormalizer()
 *       .andThen(TextPreprocessors.whitespaceNormalizer())
 *       .andThen(TextPreprocessors.lowercaseNormalizer());
 *   CamundaAssert.setSemanticSimilarityConfig(
 *       SemanticSimilarityConfig.of(model).withPreprocessors(pipeline));
 * </pre>
 */
public final class TextPreprocessors {

  private TextPreprocessors() {}

  /**
   * Returns a {@link TextPreprocessor} instance that normalizes whitespaces by trimming
   * leading/trailing whitespace and collapsing all internal whitespace sequences (spaces, tabs,
   * newlines) to a single space.
   *
   * @return a whitespace-normalizing {@link TextPreprocessor}
   */
  public static TextPreprocessor whitespaceNormalizer() {
    return WhitespaceNormalizerPreprocessor.INSTANCE;
  }

  /**
   * Returns a {@link TextPreprocessor} instance that converts text to lower case using {@code
   * Locale.ROOT} to ensure locale-independent behavior.
   *
   * @return a lowercase-converting {@link TextPreprocessor}
   */
  public static TextPreprocessor lowercaseNormalizer() {
    return LowercaseNormalizerPreprocessor.INSTANCE;
  }

  /**
   * Returns a {@link TextPreprocessor} instance that applies Unicode NFC normalization to the text.
   *
   * @return a Unicode-normalizing {@link TextPreprocessor}
   */
  public static TextPreprocessor unicodeNormalizer() {
    return UnicodeNormalizerPreprocessor.INSTANCE;
  }

  /**
   * Returns the default preprocessing pipeline: Unicode normalization → whitespace normalization →
   * lowercasing.
   *
   * <p>This is the pipeline applied automatically by {@link SemanticSimilarityConfig} unless
   * overridden via {@link SemanticSimilarityConfig#withPreprocessors} or {@link
   * SemanticSimilarityConfig#withoutPreprocessors}.
   *
   * @return an immutable list of the default {@link TextPreprocessor}s in application order
   */
  public static List<TextPreprocessor> defaults() {
    return Collections.unmodifiableList(
        Arrays.asList(unicodeNormalizer(), whitespaceNormalizer(), lowercaseNormalizer()));
  }
}
