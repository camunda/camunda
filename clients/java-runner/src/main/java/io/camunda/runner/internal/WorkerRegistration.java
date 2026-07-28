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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers SDK job workers with a counting decorator and exposes per-jobType handled counts. */
public final class WorkerRegistration {

  private static final Logger LOG = LoggerFactory.getLogger(WorkerRegistration.class);

  private final List<JobWorker> openWorkers = new ArrayList<>();
  private final Map<String, AtomicLong> handled = new LinkedHashMap<>();

  /**
   * Opens one worker per binding using the prefixed job type. The handler is wrapped to increment a
   * jobs-handled counter on successful completion.
   */
  public void register(
      final CamundaClient client,
      final Map<BindingKey, BoundHandler> bindings,
      final Map<BindingKey, String> jobTypeByBinding,
      final String runId) {

    for (final Map.Entry<BindingKey, BoundHandler> entry : bindings.entrySet()) {
      final BindingKey key = entry.getKey();
      final String jobType = jobTypeByBinding.get(key);
      if (jobType == null) {
        // skipped during rewrite — should not happen for valid bindings
        continue;
      }
      final AtomicLong counter = new AtomicLong();
      handled.put(jobType, counter);

      final JobHandler base = adapter(entry.getValue());
      final JobHandler counting =
          (jobClient, job) -> {
            base.handle(jobClient, job);
            counter.incrementAndGet();
          };

      final JobWorker worker =
          client
              .newWorker()
              .jobType(jobType)
              .handler(counting)
              .name(runId + "-" + key.handleKey())
              .open();
      openWorkers.add(worker);
      LOG.info("registered worker for jobType={} (binding={})", jobType, key);
    }
  }

  private static JobHandler adapter(final BoundHandler handler) {
    return switch (handler) {
      case BoundHandler.OfFunction(var fn) -> new JobHandlerAdapter(fn);
      case BoundHandler.OfConsumer(var c) -> new JobHandlerAdapter(c);
    };
  }

  /** Snapshot of jobType -> handled count. */
  public Map<String, Long> handledSnapshot() {
    final LinkedHashMap<String, Long> out = new LinkedHashMap<>();
    handled.forEach((k, v) -> out.put(k, v.get()));
    return out;
  }

  /** Closes all open workers; safe to call multiple times. */
  public void close() {
    for (final JobWorker worker : openWorkers) {
      try {
        worker.close();
      } catch (final Exception e) {
        LOG.warn("failed to close worker", e);
      }
    }
    openWorkers.clear();
  }
}
