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
package io.camunda.runner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Handle returned by {@link LiveBpmn#run(int)} / {@link LiveBpmn#run(int, Cluster)}. Phase 2
 * surface is deliberately small; richer views (instance/worker views, progress) land in Phase 3.
 */
public interface Run extends AutoCloseable {

  /** Short, human-readable run id (e.g. {@code stephan-r7f3a}). */
  String runId();

  /** The (prefixed) BPMN process id that was deployed. */
  String processId();

  /** Process definition key returned by the deployment. Used to pin {@code createInstance}. */
  long processDefinitionKey();

  /** Wall-clock timestamp at which the {@code Run} began creating instances. */
  Instant startedAt();

  /** Process instance keys created by this run (eager, pre-await). */
  List<Long> instances();

  /**
   * Local jobs-handled counters keyed by clean elementId (e.g. {@code validate}, not {@code
   * stephan-r7f3a-validate}). Real-time, not from the secondary store.
   */
  Map<String, Long> workersHandled();

  /** A link to this run's process in Operate, ready to paste into a browser. */
  String operateUrl();

  /**
   * Blocks until every created instance reaches a terminal state (COMPLETED, TERMINATED) or carries
   * an incident, or until {@code timeout} elapses.
   */
  void await(Duration timeout);

  /**
   * Closes registered workers. The deployment is left in the cluster. If the underlying {@link
   * Cluster} owns its client, that is closed too.
   */
  @Override
  void close();
}
