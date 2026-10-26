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
package io.camunda.client.api.worker.probe;

/** Answers the probes pushed to a probe worker (alpha). */
@FunctionalInterface
public interface ProbeHandler {

  /**
   * Returns the answer, which the worker sends exactly once. A thrown exception is answered as
   * {@link ProbeAnswer#error(String)}, with the probe's secret values removed from the message.
   */
  ProbeAnswer handle(ActivatedProbe probe) throws Exception;
}
