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
package io.camunda.process.test.impl.extension;

import io.camunda.process.test.api.ConditionalScenarioActionStep;
import io.camunda.process.test.api.ConditionalScenarioConditionStep;

/** Manages conditional scenario registration, evaluation, and lifecycle. */
public class ConditionalScenarioEngine {

  public ConditionalScenarioConditionStep when(final Runnable condition) {
    return new NoOpConditionStep();
  }

  public void reset() {}

  private static final class NoOpConditionStep implements ConditionalScenarioConditionStep {
    @Override
    public ConditionalScenarioActionStep then(final Runnable action) {
      return new NoOpActionStep();
    }
  }

  private static final class NoOpActionStep implements ConditionalScenarioActionStep {
    @Override
    public ConditionalScenarioActionStep then(final Runnable action) {
      return this;
    }

    @Override
    public ConditionalScenarioConditionStep when(final Runnable condition) {
      return new NoOpConditionStep();
    }
  }
}
