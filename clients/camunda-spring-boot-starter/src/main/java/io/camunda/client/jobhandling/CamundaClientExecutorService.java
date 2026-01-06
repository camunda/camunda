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
package io.camunda.client.jobhandling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Wrapper bean for {@link ScheduledExecutorService} required in Spring Zeebe for Job Handling,
 * Retry Management and so on.
 *
 * <p>This is wrapped, so you can have multiple executor services in the Spring context and qualify
 * the right one.
 */
public class CamundaClientExecutorService {
  private final ScheduledExecutorService scheduledExecutorService;
  private final boolean scheduledExecutorOwnedByCamundaClient;

  private final ExecutorService jobHandlingExecutor;
  private final boolean jobHandlingExecutorOwnedByCamundaClient;

  /** Sets a single executor service for both scheduled tasks and job handling. */
  public CamundaClientExecutorService(
      final ScheduledExecutorService scheduledExecutorService, final boolean ownedByCamundaClient) {
    this(
        scheduledExecutorService,
        ownedByCamundaClient,
        scheduledExecutorService,
        ownedByCamundaClient);
  }

  /** Sets separate executor services for scheduled tasks and job handling. */
  public CamundaClientExecutorService(
      final ScheduledExecutorService scheduledExecutorService,
      final boolean scheduledExecutorOwnedByCamundaClient,
      final ExecutorService executorService,
      final boolean jobHandlingExecutorOwnedByCamundaClient) {
    this.scheduledExecutorService = scheduledExecutorService;
    this.scheduledExecutorOwnedByCamundaClient = scheduledExecutorOwnedByCamundaClient;
    jobHandlingExecutor = executorService;
    this.jobHandlingExecutorOwnedByCamundaClient = jobHandlingExecutorOwnedByCamundaClient;
  }

  public boolean isScheduledExecutorOwnedByCamundaClient() {
    return scheduledExecutorOwnedByCamundaClient;
  }

  public ScheduledExecutorService getScheduledExecutor() {
    return scheduledExecutorService;
  }

  public boolean isJobHandlingExecutorOwnedByCamundaClient() {
    return jobHandlingExecutorOwnedByCamundaClient;
  }

  public ExecutorService getJobHandlingExecutor() {
    return jobHandlingExecutor;
  }

  public static CamundaClientExecutorService createDefault() {
    return createDefault(1);
  }

  public static CamundaClientExecutorService createDefault(final int threads) {
    final ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(threads);
    return new CamundaClientExecutorService(threadPool, true, threadPool, true);
  }
}
