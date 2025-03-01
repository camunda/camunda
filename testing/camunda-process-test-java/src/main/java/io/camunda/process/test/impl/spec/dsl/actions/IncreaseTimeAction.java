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
package io.camunda.process.test.impl.spec.dsl.actions;

import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.spec.SpecTestContext;
import io.camunda.process.test.impl.spec.dsl.AbstractSpecInstruction;
import io.camunda.process.test.impl.spec.dsl.SpecAction;
import java.time.Duration;
import java.time.format.DateTimeParseException;

public class IncreaseTimeAction extends AbstractSpecInstruction implements SpecAction {

  private String duration;

  public String getDuration() {
    return duration;
  }

  public void setDuration(final String duration) {
    this.duration = duration;
  }

  @Override
  public void execute(
      final SpecTestContext testContext, final CamundaProcessTestContext processTestContext) {

    try {

      final Duration timeToAdd = Duration.parse(duration);
      processTestContext.increaseTime(timeToAdd);

    } catch (final DateTimeParseException e) {
      throw new IllegalArgumentException(String.format("Invalid duration format: '%s'", duration));
    }
  }
}
