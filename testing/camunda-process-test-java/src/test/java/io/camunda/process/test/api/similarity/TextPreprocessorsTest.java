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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.similarity.preprocessors.LowercaseNormalizerPreprocessor;
import io.camunda.process.test.impl.similarity.preprocessors.UnicodeNormalizerPreprocessor;
import io.camunda.process.test.impl.similarity.preprocessors.WhitespaceNormalizerPreprocessor;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TextPreprocessorsTest {

  @Test
  void shouldApplyPreprocessorsInOrder() {
    final TextPreprocessor pipeline =
        WhitespaceNormalizerPreprocessor.INSTANCE
            .andThen(text -> text.replaceAll("HELLO", "HI "))
            .andThen(text -> text.replaceAll("HI", "YO"))
            .andThen(LowercaseNormalizerPreprocessor.INSTANCE);

    assertThat(pipeline.process("HELLO WORLD")).isEqualTo("yo  world");
  }

  @Test
  void shouldPassNullThrough() {
    final TextPreprocessor pipeline =
        WhitespaceNormalizerPreprocessor.INSTANCE.andThen(LowercaseNormalizerPreprocessor.INSTANCE);

    assertThat(pipeline.process(null)).isNull();
  }

  @Test
  void shouldReturnWhitespaceNormalizer() {
    Assertions.assertThat(TextPreprocessors.whitespaceNormalizer().process("  a  b  "))
        .isEqualTo("a b");
  }

  @Test
  void shouldReturnLowercaseNormalizer() {
    assertThat(TextPreprocessors.lowercaseNormalizer().process("ABC")).isEqualTo("abc");
  }

  @Test
  void shouldReturnUnicodeNormalizer() {
    assertThat(TextPreprocessors.unicodeNormalizer().process("e\u0301")).isEqualTo("\u00E9");
  }

  @Test
  void shouldReturnDefaultPipelineWithThreePreprocessors() {
    final List<TextPreprocessor> defaults = TextPreprocessors.defaults();
    assertThat(defaults).hasSize(3);
    assertThat(defaults.get(0)).isInstanceOf(UnicodeNormalizerPreprocessor.class);
    assertThat(defaults.get(1)).isInstanceOf(WhitespaceNormalizerPreprocessor.class);
    assertThat(defaults.get(2)).isInstanceOf(LowercaseNormalizerPreprocessor.class);
  }
}
