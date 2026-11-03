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

import io.camunda.client.api.worker.BackoffSupplier;
import java.time.Duration;
import java.util.List;

/**
 * Builds a probe worker (alpha). Unlike a job worker, which polls for one job type, one probe
 * worker answers several probe IDs, each with its own handler, and asks for all of them in one
 * poll: probes are rare, so a poll per probe ID would mostly come back empty.
 */
public interface ProbeWorkerBuilderStep1 {

  /**
   * A probe ID to answer, matched exactly, and the handler that answers its probes. By convention
   * the ID is {@code <operation>/<template>}, e.g. {@code
   * validate-configuration/io.camunda:aws-credential:1}; the cluster does not enforce it.
   *
   * @throws IllegalArgumentException if the probe ID was already added
   */
  ProbeWorkerBuilderStep2 probeId(String probeId, ProbeHandler handler);

  interface ProbeWorkerBuilderStep2 {

    /**
     * Another probe ID to answer in the same poll, and its handler.
     *
     * @throws IllegalArgumentException if the probe ID was already added
     */
    ProbeWorkerBuilderStep2 probeId(String probeId, ProbeHandler handler);

    /** The name recorded on the probes this worker collects; defaults to the client's. */
    ProbeWorkerBuilderStep2 name(String workerName);

    /** The tenants to answer probes for; defaults to the client's default tenant. */
    ProbeWorkerBuilderStep2 tenantIds(List<String> tenantIds);

    ProbeWorkerBuilderStep2 tenantIds(String... tenantIds);

    /**
     * How many probes are handled at the same time, across all probe IDs; the worker polls only for
     * free slots.
     */
    ProbeWorkerBuilderStep2 maxProbesActive(int maxProbesActive);

    /** How long to wait between two polls that returned no probe. */
    ProbeWorkerBuilderStep2 pollInterval(Duration pollInterval);

    /** How long the gateway holds a poll open when no probe is waiting (long polling). */
    ProbeWorkerBuilderStep2 requestTimeout(Duration requestTimeout);

    /**
     * The delay before polling again after a failed poll, including a 404 from a cluster without
     * probes; exponential by default.
     */
    ProbeWorkerBuilderStep2 backoffSupplier(BackoffSupplier backoffSupplier);

    ProbeWorker open();
  }
}
