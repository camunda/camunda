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

import org.junit.jupiter.api.Test;

public class LowercaseNormalizerPreprocessorTest {

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
