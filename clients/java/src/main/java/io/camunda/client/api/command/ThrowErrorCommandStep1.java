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

import io.camunda.client.api.response.ThrowErrorResponse;
import java.io.InputStream;
import java.util.Map;

public interface ThrowErrorCommandStep1
    extends CommandWithCommunicationApiStep<ThrowErrorCommandStep1> {
  /**
   * Set the errorCode for the error.
   *
   * <p>If the errorCode can't be matched to an error catch event in the process, an incident will
   * be created.
   *
   * @param errorCode the errorCode that will be matched against an error catch event
   * @return the builder for this command. Call {@link ThrowErrorCommandStep2#send()} to complete
   *     the command and send it to the broker.
   */
  ThrowErrorCommandStep2 errorCode(String errorCode);

  interface ThrowErrorCommandStep2
      extends FinalCommandStep<ThrowErrorResponse>, CommandWithVariables<ThrowErrorCommandStep2> {
    /**
     * Provide an error message describing the reason for the non-technical error. If the error is
     * not caught by an error catch event, this message will be a part of the raised incident.
     *
     * @param errorMsg error message
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    ThrowErrorCommandStep2 errorMessage(String errorMsg);

    /**
     * Set the variables of this job.
     *
     * @param variables the variables (JSON) as String
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variables(String variables);

    /**
     * Set the variables of this job.
     *
     * @param variables the variables as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variables(Object variables);

    /**
     * Set the variables of this job.
     *
     * @param variables the variables (JSON) as stream
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variables(InputStream variables);

    /**
     * Set the variables of this job.
     *
     * @param variables the variables as map
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variables(Map<String, Object> variables);

    /**
     * Set a single variable of this job.
     *
     * @param key the key of the variable as string
     * @param value the value of the variable as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    @Override
    ThrowErrorCommandStep2 variable(String key, Object value);
  }
}
