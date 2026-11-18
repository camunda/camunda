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

/**
 * Answers the probes of one probe ID (alpha). The worker sends the returned answer, so a handler
 * can neither forget to answer nor answer twice.
 *
 * <p>A handler that throws gets the answer of the worker's {@link ProbeExceptionHandler}: by
 * default {@code ERROR}, with the exception's message without the probe's secret values.
 */
@FunctionalInterface
public interface ProbeHandler {

  /**
   * @param probe the probe, with the secret values injected into its input
   * @return the answer to send, never null
   * @throws Exception if the handler cannot answer; the worker answers {@code ERROR} instead
   */
  ProbeAnswer handle(ActivatedProbe probe) throws Exception;
}
