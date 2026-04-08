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
package io.camunda.process.test.api.assertions;

import io.camunda.process.test.api.judge.JudgeConfig;
import java.util.function.UnaryOperator;

/** The assertion object to evaluate raw string-based values. */
public interface ValueAssert {

  /**
   * Overrides the {@link JudgeConfig} for subsequent evaluations in this chain. The modifier
   * receives the current config and returns a new one. Does not affect the global config.
   *
   * @param modifier function that transforms the current config
   * @return this assertion object
   */
  ValueAssert withJudgeConfig(UnaryOperator<JudgeConfig> modifier);

  /**
   * Verifies that the actual value satisfies the given natural-language expectation using the LLM
   * judge. Fails if the judge score is below the configured threshold.
   *
   * @param expectation the natural-language expectation
   * @return this assertion object
   */
  ValueAssert satisfiesJudge(String expectation);
}
