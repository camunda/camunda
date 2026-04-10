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
package io.camunda.process.test.impl.similarity.preprocessors;

import io.camunda.process.test.api.similarity.TextPreprocessor;
import java.text.Normalizer;

/**
 * A {@link TextPreprocessor} that applies Unicode NFC (Canonical Decomposition, followed by
 * Canonical Composition) normalization to text. This resolves composed and decomposed character
 * representations to a consistent form (e.g. {@code "é"} vs {@code "e\u0301"}).
 */
public final class UnicodeNormalizerPreprocessor implements TextPreprocessor {

  public static final TextPreprocessor INSTANCE = new UnicodeNormalizerPreprocessor();

  private UnicodeNormalizerPreprocessor() {}

  @Override
  public String process(final String text) {
    return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFC);
  }
}
