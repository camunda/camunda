/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.session;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.primitive.Recovery;
import io.atomix.primitive.session.SessionClient;
import io.atomix.raft.ReadConsistency;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Raft primitive proxy. */
public interface RaftSessionClient extends SessionClient {

  /** Raft proxy builder. */
  abstract class Builder extends SessionClient.Builder {

    protected Duration minTimeout = Duration.ofMillis(250);
    protected Duration maxTimeout = Duration.ofSeconds(30);
    protected ReadConsistency readConsistency = ReadConsistency.SEQUENTIAL;
    protected CommunicationStrategy communicationStrategy = CommunicationStrategy.LEADER;
    protected Recovery recoveryStrategy = Recovery.RECOVER;
    protected int maxRetries = 0;
    protected Duration retryDelay = Duration.ofMillis(100);

    /**
     * Sets the minimum session timeout.
     *
     * @param minTimeout the minimum session timeout
     * @return the Raft protocol builder
     */
    public Builder withMinTimeout(final Duration minTimeout) {
      this.minTimeout = checkNotNull(minTimeout, "minTimeout cannot be null");
      return this;
    }

    /**
     * Sets the maximum session timeout.
     *
     * @param maxTimeout the maximum session timeout
     * @return the Raft protocol builder
     */
    public Builder withMaxTimeout(final Duration maxTimeout) {
      this.maxTimeout = checkNotNull(maxTimeout, "maxTimeout cannot be null");
      return this;
    }

    /**
     * Sets the read consistency level.
     *
     * @param readConsistency the read consistency level
     * @return the Raft protocol builder
     */
    public Builder withReadConsistency(final ReadConsistency readConsistency) {
      this.readConsistency = checkNotNull(readConsistency, "readConsistency cannot be null");
      return this;
    }

    /**
     * Sets the communication strategy.
     *
     * @param communicationStrategy the communication strategy
     * @return the Raft protocol builder
     */
    public Builder withCommunicationStrategy(final CommunicationStrategy communicationStrategy) {
      this.communicationStrategy =
          checkNotNull(communicationStrategy, "communicationStrategy cannot be null");
      return this;
    }

    /**
     * Sets the recovery strategy.
     *
     * @param recoveryStrategy the recovery strategy
     * @return the Raft protocol builder
     */
    public Builder withRecoveryStrategy(final Recovery recoveryStrategy) {
      this.recoveryStrategy = checkNotNull(recoveryStrategy, "recoveryStrategy cannot be null");
      return this;
    }

    /**
     * Sets the maximum number of retries before an operation can be failed.
     *
     * @param maxRetries the maximum number of retries before an operation can be failed
     * @return the proxy builder
     */
    public Builder withMaxRetries(final int maxRetries) {
      checkArgument(maxRetries >= 0, "maxRetries must be positive");
      this.maxRetries = maxRetries;
      return this;
    }

    /**
     * Sets the operation retry delay.
     *
     * @param retryDelayMillis the delay between operation retries in milliseconds
     * @return the proxy builder
     */
    public Builder withRetryDelayMillis(final long retryDelayMillis) {
      return withRetryDelay(Duration.ofMillis(retryDelayMillis));
    }

    /**
     * Sets the operation retry delay.
     *
     * @param retryDelay the delay between operation retries
     * @return the proxy builder
     * @throws NullPointerException if the delay is null
     */
    public Builder withRetryDelay(final Duration retryDelay) {
      this.retryDelay = checkNotNull(retryDelay, "retryDelay cannot be null");
      return this;
    }

    /**
     * Sets the operation retry delay.
     *
     * @param retryDelay the delay between operation retries
     * @param timeUnit the delay time unit
     * @return the proxy builder
     * @throws NullPointerException if the time unit is null
     */
    public Builder withRetryDelay(final long retryDelay, final TimeUnit timeUnit) {
      return withRetryDelay(Duration.ofMillis(timeUnit.toMillis(retryDelay)));
    }
  }
}
