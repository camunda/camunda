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

/** Whether the worker of a probe produced an answer (alpha). */
public enum ProbeOutcome {
  /** The worker answered; the result is in the data. */
  OK,
  /** The worker collected the probe but cannot answer this input. */
  UNSUPPORTED,
  /** The worker failed to answer; the reason is in the message. */
  ERROR
}
