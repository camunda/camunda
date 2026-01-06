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
package io.camunda.client.api.command;

import io.camunda.client.api.command.EvaluateConditionalCommandStep1.EvaluateConditionalCommandStep2;
import io.camunda.client.api.response.EvaluateConditionalResponse;

public interface EvaluateConditionalCommandStep1
    extends CommandWithCommunicationApiStep<EvaluateConditionalCommandStep1>,
        CommandWithVariables<EvaluateConditionalCommandStep2> {

  public interface EvaluateConditionalCommandStep2
      extends CommandWithTenantStep<EvaluateConditionalCommandStep2>,
          FinalCommandStep<EvaluateConditionalResponse> {

    /**
     * Set the key of the process definition to evaluate conditional start events for. If a key is
     * not set, the root-level conditional start events across all process definitions will be
     * evaluated.
     *
     * <p>This value will be used to evaluate only the conditional start events of the specified
     * process definition.
     *
     * @param processDefinitionKey the key of the process definition
     * @return the builder for this command
     */
    EvaluateConditionalCommandStep2 processDefinitionKey(long processDefinitionKey);
  }
}
