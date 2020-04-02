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
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.PrimitiveProtocolConfig;
import io.atomix.raft.session.CommunicationStrategy;
import java.time.Duration;

/** Raft protocol configuration. */
public class MultiRaftProtocolConfig extends PrimitiveProtocolConfig<MultiRaftProtocolConfig> {

  private String group;
  private Partitioner<String> partitioner = Partitioner.MURMUR3;
  private Duration minTimeout = Duration.ofMillis(250);
  private Duration maxTimeout = Duration.ofSeconds(30);
  private ReadConsistency readConsistency = ReadConsistency.SEQUENTIAL;
  private CommunicationStrategy communicationStrategy = CommunicationStrategy.LEADER;
  private Recovery recoveryStrategy = Recovery.RECOVER;
  private int maxRetries = 0;
  private Duration retryDelay = Duration.ofMillis(100);

  /**
   * Returns the client communication strategy.
   *
   * @return the client communication strategy
   */
  public CommunicationStrategy getCommunicationStrategy() {
    return communicationStrategy;
  }

  /**
   * Sets the client communication strategy.
   *
   * @param communicationStrategy the client communication strategy
   * @return the Raft protocol configuration
   */
  public MultiRaftProtocolConfig setCommunicationStrategy(
      final CommunicationStrategy communicationStrategy) {
    this.communicationStrategy = communicationStrategy;
    return this;
  }

  /**
   * Returns the partition group.
   *
   * @return the partition group
   */
  public String getGroup() {
    return group;
  }

  /**
   * Sets the partition group.
   *
   * @param group the partition group
   * @return the protocol configuration
   */
  public MultiRaftProtocolConfig setGroup(final String group) {
    this.group = group;
    return this;
  }

  /**
   * Returns the maximum allowed number of retries.
   *
   * @return the maximum allowed number of retries
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Sets the maximum allowed number of retries.
   *
   * @param maxRetries the maximum allowed number of retries
   * @return the protocol configuration
   */
  public MultiRaftProtocolConfig setMaxRetries(final int maxRetries) {
    this.maxRetries = maxRetries;
    return this;
  }

  /**
   * Returns the maximum session timeout.
   *
   * @return the maximum session timeout
   */
  public Duration getMaxTimeout() {
    return maxTimeout;
  }

  /**
   * Sets the maximum session timeout.
   *
   * @param maxTimeout the maximum session timeout
   * @return the Raft protocol configuration
   */
  public MultiRaftProtocolConfig setMaxTimeout(final Duration maxTimeout) {
    this.maxTimeout = maxTimeout;
    return this;
  }

  /**
   * Returns the minimum session timeout.
   *
   * @return the minimum session timeout
   */
  public Duration getMinTimeout() {
    return minTimeout;
  }

  /**
   * Sets the minimum session timeout.
   *
   * @param minTimeout the minimum session timeout
   * @return the Raft protocol configuration
   */
  public MultiRaftProtocolConfig setMinTimeout(final Duration minTimeout) {
    this.minTimeout = minTimeout;
    return this;
  }

  /**
   * Returns the protocol partitioner.
   *
   * @return the protocol partitioner
   */
  public Partitioner<String> getPartitioner() {
    return partitioner;
  }

  /**
   * Sets the protocol partitioner.
   *
   * @param partitioner the protocol partitioner
   * @return the protocol configuration
   */
  public MultiRaftProtocolConfig setPartitioner(final Partitioner<String> partitioner) {
    this.partitioner = partitioner;
    return this;
  }

  /**
   * Returns the read consistency level.
   *
   * @return the read consistency level
   */
  public ReadConsistency getReadConsistency() {
    return readConsistency;
  }

  /**
   * Sets the read consistency level.
   *
   * @param readConsistency the read consistency level
   * @return the Raft protocol configuration
   */
  public MultiRaftProtocolConfig setReadConsistency(final ReadConsistency readConsistency) {
    this.readConsistency = readConsistency;
    return this;
  }

  /**
   * Returns the client recovery strategy.
   *
   * @return the client recovery strategy
   */
  public Recovery getRecoveryStrategy() {
    return recoveryStrategy;
  }

  /**
   * Sets the client recovery strategy.
   *
   * @param recoveryStrategy the client recovery strategy
   * @return the Raft protocol configuration
   */
  public MultiRaftProtocolConfig setRecoveryStrategy(final Recovery recoveryStrategy) {
    this.recoveryStrategy = recoveryStrategy;
    return this;
  }

  /**
   * Returns the retry delay.
   *
   * @return the retry delay
   */
  public Duration getRetryDelay() {
    return retryDelay;
  }

  /**
   * Sets the retry delay.
   *
   * @param retryDelay the retry delay
   * @return the protocol configuration
   */
  public MultiRaftProtocolConfig setRetryDelay(final Duration retryDelay) {
    this.retryDelay = retryDelay;
    return this;
  }

  @Override
  public PrimitiveProtocol.Type getType() {
    return MultiRaftProtocol.TYPE;
  }

  /**
   * Sets the retry delay.
   *
   * @param retryDelayMillis the retry delay in milliseconds
   * @return the protocol configuration
   */
  public MultiRaftProtocolConfig setRetryDelayMillis(final long retryDelayMillis) {
    return setRetryDelay(Duration.ofMillis(retryDelayMillis));
  }
}
