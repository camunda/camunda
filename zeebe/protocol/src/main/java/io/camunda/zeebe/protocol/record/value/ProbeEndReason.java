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
package io.camunda.zeebe.protocol.record.value;

/** Why the engine ended a probe without an answer. */
public enum ProbeEndReason {
  /** {@code TIMED_OUT}: no worker collected the probe before its deadline. */
  NO_WORKER,
  /** {@code TIMED_OUT}: the probe still waited for its secrets at its deadline. */
  SECRET_RESOLUTION_TIMEOUT,
  /** {@code TIMED_OUT}: a worker collected the probe but did not answer before its deadline. */
  WORKER_DID_NOT_ANSWER,
  /** {@code CANCELED}: a referenced secret could not be resolved or injected into the input. */
  SECRET_RESOLUTION_ERROR,
  /** The probe has not ended, or ended with an answer. */
  UNSPECIFIED
}
