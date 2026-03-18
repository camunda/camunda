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
package io.camunda.process.test.api;

import io.camunda.process.test.api.assertions.EvaluationAssert;
import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.impl.assertions.EvaluationAssertj;

/**
 * Entry point for standalone LLM judge assertions on string values. Uses the global {@link
 * JudgeConfig} from {@link CamundaAssert} by default.
 *
 * @see JudgeConfig
 * @see CamundaAssert#setJudgeConfig(JudgeConfig)
 */
public final class EvaluationAssertions {

  private EvaluationAssertions() {}

  /**
   * Creates a new judge assertion for the given value.
   *
   * @param actual the string value to evaluate
   * @return the assertion object
   */
  public static EvaluationAssert assertThat(final String actual) {
    return new EvaluationAssertj(actual, CamundaAssert.getJudgeConfig());
  }
}
