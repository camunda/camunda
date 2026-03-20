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

import io.camunda.process.test.api.assertions.EvaluationAssert;
import io.camunda.process.test.api.judge.JudgeConfig;
import java.util.function.UnaryOperator;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ implementation of {@link EvaluationAssert}. Evaluates string values against
 * natural-language expectations using an LLM judge.
 */
public class EvaluationAssertj extends AbstractAssert<EvaluationAssertj, String>
    implements EvaluationAssert {

  private final JudgeAssertj judgeAssertj;

  public EvaluationAssertj(final String actual, final JudgeConfig judgeConfig) {
    super(actual, EvaluationAssertj.class);
    this.judgeAssertj = new JudgeAssertj(judgeConfig);
  }

  @Override
  public EvaluationAssert withJudgeConfig(final UnaryOperator<JudgeConfig> modifier) {
    judgeAssertj.withJudgeConfig(modifier);
    return this;
  }

  @Override
  public EvaluationAssert satisfiesExpectation(final String expectation) {
    judgeAssertj.assertJudgeHasAllRequiredSettings();
    JudgeAssertj.assertExpectationNotEmpty(expectation);
    judgeAssertj.evaluateExpectation(expectation, actual, "");
    return this;
  }
}
