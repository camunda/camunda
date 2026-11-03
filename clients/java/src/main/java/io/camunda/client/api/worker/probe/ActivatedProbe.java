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

import java.util.Map;

/**
 * A probe pushed to a probe worker (alpha). The input arrives with the secret values already
 * injected, so {@link #toString()} leaves it out; {@link #toJson()} is complete.
 */
public interface ActivatedProbe {

  long getKey();

  /** The ID of the probe, which selected the handler. */
  String getProbeId();

  /** The name of the worker the probe was pushed to. */
  String getWorker();

  /** When the client stops waiting, as a UNIX epoch timestamp. */
  long getDeadline();

  String getTenantId();

  /**
   * @return the identifier of the physical tenant whose stream the probe was pushed on; the default
   *     physical tenant when the worker's client did not specify one
   */
  String getPhysicalTenantId();

  /** The evaluated input as a JSON document, with the secret values injected. */
  String getInput();

  Map<String, Object> getInputAsMap();

  <T> T getInputAsType(Class<T> inputType);

  String toJson();
}
