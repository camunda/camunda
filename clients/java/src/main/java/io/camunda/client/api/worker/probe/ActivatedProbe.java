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

import java.util.List;
import java.util.Map;

/**
 * A probe collected by a probe worker (alpha). Its input arrives with the secret values injected by
 * the cluster: {@link #toString()} prints the probe without them, {@link #toJson()} is complete.
 */
public interface ActivatedProbe {

  /**
   * @return the key of the probe, to answer it with
   */
  long getKey();

  /**
   * @return the probe ID, which selected the handler
   */
  String getProbeId();

  /**
   * @return the name of the worker that collected the probe, returned to the client in the answer
   */
  String getWorker();

  /**
   * @return when the client stops waiting, as a UNIX epoch timestamp in milliseconds; an answer
   *     after it is rejected
   */
  long getDeadline();

  String getTenantId();

  /**
   * @return the identifier of the physical tenant the probe was collected from; the default
   *     physical tenant when the worker's client did not specify one
   */
  String getPhysicalTenantId();

  /**
   * @return the JSON pointers (RFC 6901) of the input values into which the cluster injected a
   *     secret value; empty when none
   */
  List<String> getSecretPointers();

  /**
   * @return the evaluated input as a JSON document, with the secret values injected
   */
  String getInput();

  /**
   * @return the evaluated input, with the secret values injected
   */
  Map<String, Object> getInputAsMap();

  /**
   * @return the evaluated input bound to the given type, with the secret values injected
   */
  <T> T getInputAsType(Class<T> inputType);

  /**
   * @return the probe as JSON, with the secret values injected
   */
  String toJson();
}
