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
package io.camunda.client.api.response;

import io.camunda.client.api.ExperimentalApi;
import java.util.Map;

/** The answer of the worker to a probe (alpha). */
@ExperimentalApi("https://github.com/camunda/camunda/issues/68869")
public interface ProbeResult {

  ProbeOutcome getOutcome();

  /**
   * @return the name of the worker that answered
   */
  String getWorker();

  /**
   * @return the result of the worker, whose shape the worker of the probe ID defines, if the
   *     outcome is {@link ProbeOutcome#OK}; null otherwise
   */
  Map<String, Object> getData();

  /**
   * @return why the worker failed, if the outcome is {@link ProbeOutcome#ERROR}; null otherwise
   */
  String getMessage();
}
