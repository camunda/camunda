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
package io.zeebe.client.api.commands;

import java.io.InputStream;
import java.util.Map;

public interface CompleteJobCommandStep1 extends FinalCommandStep<Void> {

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables (JSON) as stream
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variables(InputStream variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables (JSON) as String
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variables(String variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables as map
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variables(Map<String, Object> variables);

  /**
   * Set the variables to complete the job with.
   *
   * @param variables the variables as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CompleteJobCommandStep1 variables(Object variables);
}
