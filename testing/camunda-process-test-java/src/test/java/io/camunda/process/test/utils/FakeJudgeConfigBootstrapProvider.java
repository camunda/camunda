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
package io.camunda.process.test.utils;

import io.camunda.process.test.api.judge.JudgeConfig;
import io.camunda.process.test.api.judge.JudgeConfigBootstrapData;
import io.camunda.process.test.api.judge.JudgeConfigBootstrapProvider;

/**
 * A test-scoped {@link JudgeConfigBootstrapProvider} registered via SPI for verifying ServiceLoader
 * discovery in {@code JudgeAssertBootstrapIT}. Always returns a fake {@link JudgeConfig} that
 * scores 1.0.
 */
public class FakeJudgeConfigBootstrapProvider implements JudgeConfigBootstrapProvider {

  @Override
  public JudgeConfig bootstrap(final JudgeConfigBootstrapData data) {
    return JudgeConfig.of(prompt -> "{\"score\": 1.0, \"reasoning\": \"fake\"}")
        .withCustomPrompt(data.getCustomPrompt())
        .withThreshold(data.getThreshold());
  }
}
