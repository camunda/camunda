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
 * Builds a probe worker (alpha), as {@code newWorker().jobType(type).handler(handler)} builds a job
 * worker. One probe worker polls for one probe ID.
 */
public interface ProbeWorkerBuilderStep1 {

  /**
   * The probe ID to answer, matched exactly. By convention it is {@code <operation>/<template>},
   * e.g. {@code validate-configuration/io.camunda:aws-credential:1}; the cluster does not enforce
   * it.
   */
  ProbeWorkerBuilderStep2 probeId(String probeId);

  interface ProbeWorkerBuilderStep2 {

    ProbeWorkerBuilderStep3 handler(ProbeHandler handler);
  }

  interface ProbeWorkerBuilderStep3 {

    /** The name recorded on the probes pushed to this worker; defaults to the client's. */
    ProbeWorkerBuilderStep3 name(String workerName);

    /** The tenants to answer probes for; defaults to the client's default tenant. */
    ProbeWorkerBuilderStep3 tenantIds(List<String> tenantIds);

    ProbeWorkerBuilderStep3 tenantIds(String... tenantIds);

    /** How many probes are handled at the same time; the worker polls only for free slots. */
    ProbeWorkerBuilderStep3 maxProbesActive(int maxProbesActive);

    /** How long to wait between two polls that returned no probe. */
    ProbeWorkerBuilderStep3 pollInterval(Duration pollInterval);

    /** How long the gateway holds a poll open when no probe is waiting (long polling). */
    ProbeWorkerBuilderStep3 requestTimeout(Duration requestTimeout);

    /**
     * The delay before polling again after a failed poll, including a 404 from a cluster without
     * probes; exponential by default.
     */
    ProbeWorkerBuilderStep3 backoffSupplier(BackoffSupplier backoffSupplier);

    ProbeWorker open();
  }
}
