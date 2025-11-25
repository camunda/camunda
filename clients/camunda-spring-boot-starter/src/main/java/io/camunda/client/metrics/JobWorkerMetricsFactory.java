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
package io.camunda.client.metrics;

import io.camunda.client.api.worker.JobWorkerMetrics;

/**
 * Factory interface for creating {@link JobWorkerMetrics} instances.
 *
 * <p>This is a key extension point for connectors and other integrations to provide custom metrics
 * implementations for job workers. Implementations of this interface can be registered to supply
 * metrics tailored to specific job worker types or use cases.
 *
 * <p>Typical usage involves implementing this interface and providing logic in {@link
 * #createJobWorkerMetrics(JobWorkerMetricsFactoryContext)} to return a suitable {@link
 * JobWorkerMetrics} instance based on the provided context.
 */
public interface JobWorkerMetricsFactory {
  /**
   * Creates a new {@link JobWorkerMetrics} instance for the given context.
   *
   * @param context the context containing information (such as the job worker type) for which
   *     metrics should be created
   * @return a {@link JobWorkerMetrics} instance appropriate for the given context
   */
  JobWorkerMetrics createJobWorkerMetrics(JobWorkerMetricsFactoryContext context);

  /**
   * Context information provided to {@link JobWorkerMetricsFactory} when creating a new {@link
   * JobWorkerMetrics} instance.
   *
   * @param type the type of the job worker for which metrics are being created. This typically
   *     corresponds to the job type handled by the worker.
   */
  record JobWorkerMetricsFactoryContext(String type) {}
}
