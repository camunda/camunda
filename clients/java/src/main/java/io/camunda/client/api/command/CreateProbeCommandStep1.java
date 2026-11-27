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

import io.camunda.client.api.response.ProbeResult;
import java.time.Duration;
import java.util.Map;

public interface CreateProbeCommandStep1 {

  /**
   * Set the probe ID, as the worker registered it: 1 to 256 characters, without whitespace, and not
   * {@code *}. By convention {@code <operation>/<template>}, for example {@code
   * validate-configuration/io.camunda:aws-credential:1}.
   *
   * @param probeId the probe ID
   * @return the builder for this command
   */
  CreateProbeCommandStep2 probeId(String probeId);

  interface CreateProbeCommandStep2 {

    /**
     * Set the input as a FEEL expression that evaluates to a context, for example {@code ={region:
     * "eu-west-1", accessKey: camunda.secrets.awsKey}}. References to {@code camunda.vars} and
     * {@code camunda.secrets} follow the rules of input mappings.
     *
     * @param expression the FEEL expression, starting with {@code =}
     * @return the builder for this command
     */
    CreateProbeCommandStep3 input(String expression);

    /**
     * Set the input as a context of literal values: the map is sent as its JSON, so a value never
     * becomes an expression or a reference.
     *
     * @param input the input
     * @return the builder for this command
     */
    CreateProbeCommandStep3 input(Map<String, Object> input);

    /**
     * Set the input as a context of literal values: the object is serialized to JSON and sent as it
     * is, so a value never becomes an expression or a reference. It must serialize to a JSON
     * object.
     *
     * @param input the input
     * @return the builder for this command
     */
    CreateProbeCommandStep3 input(Object input);
  }

  interface CreateProbeCommandStep3
      extends CommandWithTenantStep<CreateProbeCommandStep3>, FinalCommandStep<ProbeResult> {

    /**
     * Set how long to wait for the worker's answer, at most 30 seconds. Without it, the cluster's
     * request timeout applies. The HTTP request waits one second longer, so that the client gets
     * the cluster's answer, including {@code 404 NO_WORKER} at the deadline.
     *
     * @param requestTimeout how long to wait for the answer
     * @return the builder for this command
     */
    @Override
    FinalCommandStep<ProbeResult> requestTimeout(Duration requestTimeout);
  }
}
