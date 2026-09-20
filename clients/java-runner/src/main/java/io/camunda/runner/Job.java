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

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.runner.internal.DefaultJob;
import java.util.Map;

/**
 * A LiveBpmn-flavoured view over an {@link ActivatedJob} that exposes both the data of the job and
 * the control verbs ({@code complete}/{@code fail}) needed to resolve it.
 *
 * <p>This is the type passed into user lambdas registered via {@link LiveBpmn#serviceTask} and
 * friends. A job may only be resolved once; calling any of {@code complete*}/{@code fail*} a second
 * time results in an {@link IllegalStateException}.
 */
public interface Job {

  long getKey();

  String getType();

  long getProcessInstanceKey();

  String getElementId();

  int getRetries();

  Map<String, Object> variables();

  <T> T variable(String name, Class<T> type);

  String variablesAsJson();

  void complete();

  void complete(Map<String, Object> variables);

  void complete(String key, Object value);

  void fail(String reason);

  void fail(String reason, int retries);

  /**
   * Escape hatch onto the underlying SDK record. Use when you need fields LiveBpmn doesn't wrap —
   * e.g. user-task metadata via {@code job.raw().getUserTask().getUserTaskKey()}, or the raw
   * variables payload, or the deadline. Never null.
   */
  ActivatedJob raw();

  /** Default factory used by the runner adapter and by tests. */
  static Job of(final JobClient client, final ActivatedJob activatedJob) {
    return new DefaultJob(client, activatedJob);
  }
}
