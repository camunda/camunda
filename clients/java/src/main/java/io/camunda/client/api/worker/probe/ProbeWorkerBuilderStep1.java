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

import java.time.Duration;
import java.util.List;

/**
 * Builds a probe worker (alpha), as {@code newWorker()} builds a job worker. One worker answers one
 * capability, one operation for one template; several templates need several workers.
 */
public interface ProbeWorkerBuilderStep1 {

  ProbeWorkerBuilderStep2 operation(ProbeOperation operation);

  interface ProbeWorkerBuilderStep2 {

    /** The template, by its declared ID, e.g. {@code io.camunda:aws-credential:1}. */
    ProbeWorkerBuilderStep3 template(String template);
  }

  interface ProbeWorkerBuilderStep3 {

    ProbeWorkerBuilderStep3 handler(ProbeHandler handler);

    /** The name recorded on the probes pushed to this worker; defaults to the client's. */
    ProbeWorkerBuilderStep3 name(String workerName);

    /** The tenants to answer probes for; defaults to the client's default tenant. */
    ProbeWorkerBuilderStep3 tenantIds(List<String> tenantIds);

    ProbeWorkerBuilderStep3 tenantIds(String... tenantIds);

    /** How many probes are handled at the same time; further pushes wait for a free slot. */
    ProbeWorkerBuilderStep3 maxProbesActive(int maxProbesActive);

    /** How long to wait before opening the stream again after it failed. */
    ProbeWorkerBuilderStep3 streamRetryDelay(Duration retryDelay);

    ProbeWorker open();
  }
}
