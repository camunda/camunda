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

package io.camunda.process.test.api.similarity.preprocessors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextPreprocessorsTest {

  @Test
  void shouldApplyPreprocessorsInOrder() {
    final TextPreprocessor pipeline =
        new WhitespaceNormalizerPreprocessor()
            .andThen(text -> text.replaceAll("HELLO", "HI "))
            .andThen(text -> text.replaceAll("HI", "YO"))
            .andThen(new LowercaseNormalizerPreprocessor());

    assertThat(pipeline.process("HELLO WORLD")).isEqualTo("yo  world");
  }

  @Test
  void shouldPassNullThrough() {
    final TextPreprocessor pipeline =
        new WhitespaceNormalizerPreprocessor().andThen(new LowercaseNormalizerPreprocessor());

    assertThat(pipeline.process(null)).isNull();
  }

  @Test
  void shouldReturnWhitespaceNormalizer() {
    assertThat(TextPreprocessors.whitespaceNormalizer().process("  a  b  ")).isEqualTo("a b");
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
