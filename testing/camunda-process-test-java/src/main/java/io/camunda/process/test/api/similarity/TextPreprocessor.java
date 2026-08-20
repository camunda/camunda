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

/**
 * A functional interface for pre-processing text before computing embedding vectors.
 *
 * <p>Implementations can be chained using {@link #andThen(TextPreprocessor)} to build a pipeline:
 *
 * <pre>
 *   TextPreprocessor pipeline = TextPreprocessors.unicodeNormalizer()
 *       .andThen(TextPreprocessors.whitespaceNormalizer())
 *       .andThen(TextPreprocessors.lowercaseNormalizer());
 * </pre>
 *
 * <p>Built-in implementations are available via {@link TextPreprocessors}.
 */
@FunctionalInterface
public interface TextPreprocessor {

  /**
   * Processes the given text.
   *
   * @param text the input text to process
   * @return the processed text
   */
  String process(String text);

  /**
   * Returns a composed preprocessor that applies this preprocessor first, then the {@code next}
   * preprocessor.
   *
   * @param next the preprocessor to apply after this one
   * @return a composed preprocessor
   */
  default TextPreprocessor andThen(final TextPreprocessor next) {
    return text -> next.process(process(text));
  }
}
