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
package io.zeebe.client.api.worker;

import io.zeebe.client.impl.worker.ExponentialBackoffBuilderImpl;
import java.time.Duration;

/**
 * The {@link JobWorker} uses this interface to determine the retry delay after each failed request.
 * After a successful request, or if no requests have been sent yet, the delay is reset to the job
 * worker's polling interval (see {@link
 * io.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3#pollInterval(Duration)}).
 *
 * <p>The supplier is called after a failed request. The worker will then await the supplied delay
 * before sending the next request.
 */
@FunctionalInterface
public interface BackoffSupplier {

  /**
   * @return a builder to configure and create a new exponential backoff {@link
   *     io.zeebe.client.impl.worker.ExponentialBackoff}.
   */
  static ExponentialBackoffBuilder newBackoffBuilder() {
    return new ExponentialBackoffBuilderImpl();
  }

  /**
   * Returns the delay before the next retry. The delay should be specified in milliseconds.
   *
   * @param currentRetryDelay the last used retry delay
   * @return the new retry delay
   */
  long supplyRetryDelay(final long currentRetryDelay);
}
