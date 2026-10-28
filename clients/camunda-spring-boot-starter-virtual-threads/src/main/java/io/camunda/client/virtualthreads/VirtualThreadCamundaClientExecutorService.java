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
package io.camunda.client.virtualthreads;

import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.jobhandling.VirtualThreadExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link CamundaClientExecutorService} whose job-handling executor runs on Java 21 virtual
 * threads, with a single platform thread for scheduling.
 *
 * <p>This is the canonical virtual-thread executor for the Camunda client. It implements {@link
 * VirtualThreadExecutorService} so the multi-client factory recognises it by type — not by sniffing
 * the job-handling executor's class name, which cannot distinguish a virtual-thread factory from a
 * platform-thread one (both back the JDK's {@code ThreadPerTaskExecutor}, and {@code
 * ExecutorService#isVirtualThreadPerTaskExecutor()} only exists on Java 24+ while the starter runs
 * on Java 17).
 *
 * <p>This class lives in the virtual-threads module because it builds the virtual-thread executor
 * directly, which requires Java 21 — the module compiles at release 21, while the starter compiles
 * at release 17 and so cannot reference the virtual-thread API.
 */
public class VirtualThreadCamundaClientExecutorService extends CamundaClientExecutorService
    implements VirtualThreadExecutorService {

  public VirtualThreadCamundaClientExecutorService(
      final ScheduledExecutorService scheduledExecutor,
      final boolean scheduledExecutorOwnedByCamundaClient,
      final ExecutorService jobHandlingExecutor,
      final boolean jobHandlingExecutorOwnedByCamundaClient) {
    super(
        scheduledExecutor,
        scheduledExecutorOwnedByCamundaClient,
        jobHandlingExecutor,
        jobHandlingExecutorOwnedByCamundaClient);
  }

  /**
   * Creates the standard virtual-thread executor: a virtual-thread job-handling executor (named
   * {@code job-worker-virtual-N}) plus a single platform-thread scheduler, both owned by the
   * client.
   */
  public static VirtualThreadCamundaClientExecutorService create() {
    final ThreadFactory virtualThreadFactory =
        Thread.ofVirtual().name("job-worker-virtual-", 0).factory();
    final ExecutorService jobHandlingExecutor =
        Executors.newThreadPerTaskExecutor(virtualThreadFactory);
    final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
    return new VirtualThreadCamundaClientExecutorService(
        scheduledExecutor, true, jobHandlingExecutor, true);
  }

  /**
   * Builds a fresh, un-metered virtual-thread executor owned by a single named client. The
   * multi-client factory calls this through the {@link VirtualThreadExecutorService} capability, so
   * the starter (which compiles at Java 17) never touches the virtual-thread API itself.
   */
  @Override
  public CamundaClientExecutorService createOwnedExecutor() {
    return create();
  }
}
