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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.api.similarity.TextPreprocessor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@Nested
class WhitespaceNormalizerPreprocessorTest {

  private final TextPreprocessor preprocessor = WhitespaceNormalizerPreprocessor.INSTANCE;

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

  @ParameterizedTest
  @NullAndEmptySource
  void shouldReturnNullAndEmptyUnchanged(final String input) {
    assertThat(preprocessor.process(input)).isEqualTo(input);
  }

  @Test
  void shouldLeaveAlreadyNormalizedTextUnchanged() {
    assertThat(preprocessor.process("hello world")).isEqualTo("hello world");
  }
}
