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

public class PublishMessageAction extends AbstractSpecInstruction implements SpecAction {

  private String messageName;
  private String correlationKey;
  private String variables = "{}";

  public String getMessageName() {
    return messageName;
  }

  public void setMessageName(final String messageName) {
    this.messageName = messageName;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public void setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
  }

  public String getVariables() {
    return variables;
  }

  public void setVariables(final String variables) {
    this.variables = variables;
  }

  @Override
  public void execute(
      final SpecTestContext testContext, final CamundaProcessTestContext processTestContext) {

    testContext
        .getCamundaClient()
        .newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .variables(variables)
        .send()
        .join();
  }
}
