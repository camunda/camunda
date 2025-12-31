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

import io.camunda.client.api.response.EvaluateDecisionResponse;
import java.io.InputStream;
import java.util.Map;

public interface EvaluateDecisionCommandStep1
    extends CommandWithCommunicationApiStep<EvaluateDecisionCommandStep1> {

  /**
   * Set the id of the decision to evaluate. This is the static id of the decision in the DMN XML
   * (i.e. "&#60;decision id='my-decision'&#62;").
   *
   * @param decisionId the DMN id of the decision
   * @return the builder for this command
   */
  EvaluateDecisionCommandStep2 decisionId(String decisionId);

  /**
   * Set the key of the decision to evaluate. The key is assigned by the broker while deploying the
   * decision. It can be picked from the deployment.
   *
   * @param decisionKey the key of the decision
   * @return the builder for this command
   */
  EvaluateDecisionCommandStep2 decisionKey(long decisionKey);

  interface EvaluateDecisionCommandStep2
      extends CommandWithTenantStep<EvaluateDecisionCommandStep2>,
          FinalCommandStep<EvaluateDecisionResponse>,
          CommandWithVariables<EvaluateDecisionCommandStep2> {

    /**
     * Set the version of the decision to evaluate. The version is assigned by the broker while
     * deploying the decision. It can be picked from the deployment or decision event. If not set,
     * the latest deployed version of the decision is used.
     *
     * @param version the version of the decision
     * @return the builder for this command
     */
    EvaluateDecisionCommandStep2 version(int version);

    /**
     * Use the latest version of the decision to evaluate.
     *
     * <p>If the latest version was deployed few moments before then it can happen that an older
     * version is evaluated.
     *
     * @return the builder for this command
     */
    EvaluateDecisionCommandStep2 latestVersion();

    /**
     * Set the variables for the decision evaluation.
     *
     * @param variables the variables JSON document as String
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateDecisionCommandStep2 variables(String variables);

    /**
     * Set the variables for the decision evaluation.
     *
     * @param variables the variables document as object to be serialized to JSON
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateDecisionCommandStep2 variables(Object variables);

    /**
     * Set the variables for the decision evaluation.
     *
     * @param variables the variables JSON document as stream
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateDecisionCommandStep2 variables(InputStream variables);

    /**
     * Set the variables for the decision evaluation.
     *
     * @param variables the variables document as map
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateDecisionCommandStep2 variables(Map<String, Object> variables);

    /**
     * Set a single variable for the decision evaluation.
     *
     * @param key the key of the variable as string
     * @param value the value of the variable as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    EvaluateDecisionCommandStep2 variable(String key, Object value);
  }
}
