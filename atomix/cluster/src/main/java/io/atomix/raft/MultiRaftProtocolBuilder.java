/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.raft;

import io.atomix.primitive.Recovery;
import io.atomix.primitive.partition.Partitioner;
import io.atomix.primitive.protocol.PrimitiveProtocolBuilder;
import io.atomix.raft.session.CommunicationStrategy;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Multi-Raft protocol builder. */
public class MultiRaftProtocolBuilder
    extends PrimitiveProtocolBuilder<
        MultiRaftProtocolBuilder, MultiRaftProtocolConfig, MultiRaftProtocol> {

  protected MultiRaftProtocolBuilder(final MultiRaftProtocolConfig config) {
    super(config);
  }

  /**
   * Sets the protocol partitioner.
   *
   * @param partitioner the protocol partitioner
   * @return the protocol builder
   */
  public MultiRaftProtocolBuilder withPartitioner(final Partitioner<String> partitioner) {
    config.setPartitioner(partitioner);
    return this;
  }

  /**
   * Sets the minimum session timeout.
   *
   * @param minTimeout the minimum session timeout
   * @return the Raft protocol builder
   */
  public MultiRaftProtocolBuilder withMinTimeout(final Duration minTimeout) {
    config.setMinTimeout(minTimeout);
    return this;
  }

  /**
   * Sets the maximum session timeout.
   *
   * @param maxTimeout the maximum session timeout
   * @return the Raft protocol builder
   */
  public MultiRaftProtocolBuilder withMaxTimeout(final Duration maxTimeout) {
    config.setMaxTimeout(maxTimeout);
    return this;
  }

  /**
   * Sets the read consistency level.
   *
   * @param readConsistency the read consistency level
   * @return the Raft protocol builder
   */
  public MultiRaftProtocolBuilder withReadConsistency(final ReadConsistency readConsistency) {
    config.setReadConsistency(readConsistency);
    return this;
  }

  /**
   * Sets the communication strategy.
   *
   * @param communicationStrategy the communication strategy
   * @return the Raft protocol builder
   */
  public MultiRaftProtocolBuilder withCommunicationStrategy(
      final CommunicationStrategy communicationStrategy) {
    config.setCommunicationStrategy(communicationStrategy);
    return this;
  }

  /**
   * Sets the recovery strategy.
   *
   * @param recoveryStrategy the recovery strategy
   * @return the Raft protocol builder
   */
  public MultiRaftProtocolBuilder withRecoveryStrategy(final Recovery recoveryStrategy) {
    config.setRecoveryStrategy(recoveryStrategy);
    return this;
  }

  /**
   * Sets the maximum number of retries before an operation can be failed.
   *
   * @param maxRetries the maximum number of retries before an operation can be failed
   * @return the proxy builder
   */
  public MultiRaftProtocolBuilder withMaxRetries(final int maxRetries) {
    config.setMaxRetries(maxRetries);
    return this;
  }

  /**
   * Sets the operation retry delay.
   *
   * @param retryDelayMillis the delay between operation retries in milliseconds
   * @return the proxy builder
   */
  public MultiRaftProtocolBuilder withRetryDelayMillis(final long retryDelayMillis) {
    return withRetryDelay(Duration.ofMillis(retryDelayMillis));
  }

  /**
   * Sets the operation retry delay.
   *
   * @param retryDelay the delay between operation retries
   * @return the proxy builder
   * @throws NullPointerException if the delay is null
   */
  public MultiRaftProtocolBuilder withRetryDelay(final Duration retryDelay) {
    config.setRetryDelay(retryDelay);
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
  public MultiRaftProtocolBuilder withRetryDelay(final long retryDelay, final TimeUnit timeUnit) {
    return withRetryDelay(Duration.ofMillis(timeUnit.toMillis(retryDelay)));
  }

  @Override
  public MultiRaftProtocol build() {
    return new MultiRaftProtocol(config);
  }
}
