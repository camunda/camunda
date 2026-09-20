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
package io.camunda.runner.internal;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.runner.Job;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Bridges a user lambda — either {@code Function<Job, Map<String, Object>>} or {@code
 * Consumer<Job>} — into the SDK's {@link JobHandler}. Applies the LiveBpmn resolution rules:
 *
 * <ul>
 *   <li>Function returning null/empty → {@code complete()}, otherwise {@code complete(map)}.
 *   <li>Consumer that did not resolve explicitly → auto {@code complete()}.
 *   <li>Uncaught exceptions propagate (SDK auto-fail path).
 * </ul>
 */
public final class JobHandlerAdapter implements JobHandler {

  private final Function<Job, Map<String, Object>> functionForm;
  private final Consumer<Job> consumerForm;

  public JobHandlerAdapter(final Function<Job, Map<String, Object>> fn) {
    this.functionForm = fn;
    this.consumerForm = null;
  }

  public JobHandlerAdapter(final Consumer<Job> consumer) {
    this.functionForm = null;
    this.consumerForm = consumer;
  }

  @Override
  public void handle(final JobClient client, final ActivatedJob activatedJob) throws Exception {
    final Job job = Job.of(client, activatedJob);
    if (functionForm != null) {
      final Map<String, Object> result = functionForm.apply(job);
      if (result == null || result.isEmpty()) {
        job.complete();
      } else {
        job.complete(result);
      }
    } else {
      consumerForm.accept(job);
      if (!isResolved(job)) {
        job.complete();
      }
    }
  }

  private static boolean isResolved(final Job job) {
    return job instanceof DefaultJob defaultJob && defaultJob.isResolved();
  }
}
