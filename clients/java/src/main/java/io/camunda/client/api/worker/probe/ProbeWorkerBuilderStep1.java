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

import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.worker.BackoffSupplier;
import java.time.Duration;
import java.util.List;

/**
 * Builds a probe worker (alpha): one worker polls for all its probe IDs in one request, and answers
 * each probe with the handler of its probe ID.
 */
public interface ProbeWorkerBuilderStep1 {

  /**
   * Adds a probe ID to answer, matched exactly, and the handler that answers its probes.
   *
   * @param probeId the probe ID: 1 to 256 characters, without whitespace, and not {@code *}. By
   *     convention {@code <operation>/<template>}, for example {@code
   *     validate-configuration/io.camunda:aws-credential:1}
   * @param handler the handler of the probes of this probe ID
   * @return the builder for this worker
   * @throws IllegalArgumentException if the probe ID is not valid or was already added
   */
  ProbeWorkerBuilderStep2 probeId(String probeId, ProbeHandler handler);

  interface ProbeWorkerBuilderStep2 {

    /**
     * Adds another probe ID, polled in the same request, and its handler. A worker has at most 100
     * probe IDs.
     *
     * @see ProbeWorkerBuilderStep1#probeId(String, ProbeHandler)
     */
    ProbeWorkerBuilderStep2 probeId(String probeId, ProbeHandler handler);

    /**
     * Set the name of the worker, returned to the client in the answer. Defaults to the client's
     * default job worker name.
     */
    ProbeWorkerBuilderStep2 name(String workerName);

    /**
     * Set the tenants to collect probes for. Defaults to the client's default job worker tenants.
     */
    ProbeWorkerBuilderStep2 tenantIds(List<String> tenantIds);

    /**
     * @see #tenantIds(List)
     */
    ProbeWorkerBuilderStep2 tenantIds(String... tenantIds);

    /**
     * Set whether the worker collects probes of the tenants it lists ({@link
     * TenantFilter#PROVIDED}) or of every tenant it is assigned to ({@link TenantFilter#ASSIGNED}).
     * Defaults to the client's default job worker tenant filter.
     */
    ProbeWorkerBuilderStep2 tenantFilter(TenantFilter tenantFilter);

    /**
     * Set how many probes the worker handles at once, across all its probe IDs; it polls only for
     * free slots. Defaults to the client's default for {@code maxJobsActive}.
     */
    ProbeWorkerBuilderStep2 maxProbesActive(int maxProbesActive);

    /**
     * Set how long to wait before the next poll after a poll that succeeded. Defaults to the
     * client's default job poll interval.
     */
    ProbeWorkerBuilderStep2 pollInterval(Duration pollInterval);

    /**
     * Set how long the cluster holds a poll open when no probe is waiting. Defaults to the client's
     * default request timeout.
     */
    ProbeWorkerBuilderStep2 requestTimeout(Duration requestTimeout);

    /**
     * Set the delay before the next poll after a failed poll, including a 404 from a cluster
     * without probes, and before sending an answer again after a 503 or a network error. Defaults
     * to an exponential backoff, as for job workers.
     */
    ProbeWorkerBuilderStep2 backoffSupplier(BackoffSupplier backoffSupplier);

    /**
     * Set what the worker answers when a handler throws. Defaults to {@link
     * ProbeExceptionHandler#createDefault()}.
     */
    ProbeWorkerBuilderStep2 probeExceptionHandler(ProbeExceptionHandler probeExceptionHandler);

    /**
     * Opens the worker: it starts polling.
     *
     * @return the worker, to close
     * @throws IllegalArgumentException if an option is not valid
     */
    ProbeWorker open();
  }
}
