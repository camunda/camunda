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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TextPreprocessorsTest {

  @Nested
  class WhitespaceNormalizerPreprocessorTest {

    private final TextPreprocessor preprocessor = new WhitespaceNormalizerPreprocessor();

    @Test
    void shouldTrimLeadingAndTrailingWhitespace() {
      assertThat(preprocessor.process("  hello  ")).isEqualTo("hello");
    }

    @Test
    void shouldCollapseInternalSpaces() {
      assertThat(preprocessor.process("hello   world")).isEqualTo("hello world");
    }

    @Test
    void shouldCollapseTabsAndNewlines() {
      assertThat(preprocessor.process("hello\t\nworld")).isEqualTo("hello world");
    }

    @Test
    void shouldReturnEmptyStringUnchanged() {
      assertThat(preprocessor.process("")).isEqualTo("");
    }

    @Test
    void shouldReturnNullForNullInput() {
      assertThat(preprocessor.process(null)).isNull();
    }

    @Test
    void shouldLeaveAlreadyNormalizedTextUnchanged() {
      assertThat(preprocessor.process("hello world")).isEqualTo("hello world");
    }
  }

  @Nested
  class UnicodeNormalizerPreprocessorTest {

    private final TextPreprocessor preprocessor = new UnicodeNormalizerPreprocessor();

    @Test
    void shouldNormalizeDecomposedCharacterToNfc() {
      // "e" + combining acute accent (U+0301) → precomposed "é" (U+00E9)
      final String decomposed = "e\u0301";
      final String composed = "\u00E9";
      assertThat(preprocessor.process(decomposed)).isEqualTo(composed);
    }

    @Test
    void shouldLeaveAlreadyNfcTextUnchanged() {
      assertThat(preprocessor.process("café")).isEqualTo("café");
    }

    @Test
    void shouldLeaveAsciiTextUnchanged() {
      assertThat(preprocessor.process("hello world")).isEqualTo("hello world");
    }

    @Test
    void shouldReturnEmptyStringUnchanged() {
      assertThat(preprocessor.process("")).isEqualTo("");
    }

    @Test
    void shouldReturnNullForNullInput() {
      assertThat(preprocessor.process(null)).isNull();
    }
  }

  @Nested
  class LowercaseNormalizerPreprocessorTest {

    private final TextPreprocessor preprocessor = new LowercaseNormalizerPreprocessor();

    @Test
    void shouldConvertUppercaseToLowercase() {
      assertThat(preprocessor.process("HELLO WORLD")).isEqualTo("hello world");
    }

    @Test
    void shouldLeaveAlreadyLowercaseTextUnchanged() {
      assertThat(preprocessor.process("hello world")).isEqualTo("hello world");
    }

    @Test
    void shouldHandleMixedCase() {
      assertThat(preprocessor.process("Hello World")).isEqualTo("hello world");
    }

    @Test
    void shouldReturnEmptyStringUnchanged() {
      assertThat(preprocessor.process("")).isEqualTo("");
    }

    @Test
    void shouldReturnNullForNullInput() {
      assertThat(preprocessor.process(null)).isNull();
    }

    @Test
    void shouldUseLocaleIndependentLowercase() {
      // Turkish locale would map 'I' to 'ı' (dotless i), ROOT locale maps it to 'i'
      assertThat(preprocessor.process("ISTANBUL")).isEqualTo("istanbul");
    }
  }

  @Nested
  class TextPreprocessorAndThenTest {

    @Test
    void shouldApplyPreprocessorsInOrder() {
      final TextPreprocessor pipeline =
          new UnicodeNormalizerPreprocessor()
              .andThen(new WhitespaceNormalizerPreprocessor())
              .andThen(new LowercaseNormalizerPreprocessor());

      assertThat(pipeline.process("  HELLO   WORLD  ")).isEqualTo("hello world");
    }

    @Test
    void shouldPassNullThrough() {
      final TextPreprocessor pipeline =
          new WhitespaceNormalizerPreprocessor().andThen(new LowercaseNormalizerPreprocessor());

      assertThat(pipeline.process(null)).isNull();
    }
  }

  @Nested
  class TextPreprocessorsFactoryTest {

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

    @Test
    void shouldApplyDefaultPipelineCorrectly() {
      final List<TextPreprocessor> defaults = TextPreprocessors.defaults();
      TextPreprocessor pipeline = defaults.get(0);
      for (int i = 1; i < defaults.size(); i++) {
        pipeline = pipeline.andThen(defaults.get(i));
      }

      // unicode → whitespace → lowercase
      assertThat(pipeline.process("  HÉLLO\t\nWORLD  ")).isEqualTo("héllo world");
    }
  }
}
