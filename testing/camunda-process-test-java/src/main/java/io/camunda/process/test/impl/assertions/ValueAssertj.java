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
package io.camunda.process.test.impl.assertions;

import io.camunda.process.test.api.assertions.ValueAssert;
import io.camunda.process.test.api.judge.JudgeConfig;
import java.util.function.UnaryOperator;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ implementation of {@link ValueAssert}. Evaluates string values against natural-language
 * expectations using an LLM judge.
 */
public class ValueAssertj extends AbstractAssert<ValueAssertj, String> implements ValueAssert {

  private final JudgeAssertj judgeAssertj;

  public ValueAssertj(final String actual, final JudgeConfig judgeConfig) {
    super(actual, ValueAssertj.class);
    judgeAssertj = new JudgeAssertj(judgeConfig);
  }

  @Override
  public ValueAssert withJudgeConfig(final UnaryOperator<JudgeConfig> modifier) {
    judgeAssertj.withJudgeConfig(modifier);
    return this;
  }

  @Override
  public ValueAssert satisfiesJudge(final String expectation) {
    judgeAssertj.assertJudgeHasAllRequiredSettings();
    assertExpectationNotEmpty(expectation);
    judgeAssertj.evaluateExpectation(expectation, actual, "");
    return this;
  }

  private static void assertExpectationNotEmpty(final String expectation) {
    if (expectation == null || expectation.trim().isEmpty()) {
      throw new IllegalArgumentException("expectation must not be null or empty");
    }
  }
}
