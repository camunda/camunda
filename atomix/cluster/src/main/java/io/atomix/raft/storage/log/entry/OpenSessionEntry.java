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
package io.atomix.raft.storage.log.entry;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.raft.ReadConsistency;
import io.atomix.utils.misc.ArraySizeHashPrinter;
import io.atomix.utils.misc.TimestampPrinter;

/** Open session entry. */
public class OpenSessionEntry extends TimestampedEntry {

  private final String memberId;
  private final String serviceName;
  private final String serviceType;
  private final byte[] serviceConfig;
  private final ReadConsistency readConsistency;
  private final long minTimeout;
  private final long maxTimeout;

  public OpenSessionEntry(
      final long term,
      final long timestamp,
      final String memberId,
      final String serviceName,
      final String serviceType,
      final byte[] serviceConfig,
      final ReadConsistency readConsistency,
      final long minTimeout,
      final long maxTimeout) {
    super(term, timestamp);
    this.memberId = memberId;
    this.serviceName = serviceName;
    this.serviceType = serviceType;
    this.serviceConfig = serviceConfig;
    this.readConsistency = readConsistency;
    this.minTimeout = minTimeout;
    this.maxTimeout = maxTimeout;
  }

  /**
   * Returns the client node identifier.
   *
   * @return The client node identifier.
   */
  public String memberId() {
    return memberId;
  }

  /**
   * Returns the session state machine name.
   *
   * @return The session's state machine name.
   */
  public String serviceName() {
    return serviceName;
  }

  /**
   * Returns the session state machine type name.
   *
   * @return The session's state machine type name.
   */
  public String serviceType() {
    return serviceType;
  }

  /**
   * Returns the service configuration.
   *
   * @return the service configuration
   */
  public byte[] serviceConfig() {
    return serviceConfig;
  }

  /**
   * Returns the session read consistency level.
   *
   * @return The session's read consistency level.
   */
  public ReadConsistency readConsistency() {
    return readConsistency;
  }

  /**
   * Returns the minimum session timeout.
   *
   * @return The minimum session timeout.
   */
  public long minTimeout() {
    return minTimeout;
  }

  /**
   * Returns the maximum session timeout.
   *
   * @return The maximum session timeout.
   */
  public long maxTimeout() {
    return maxTimeout;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("timestamp", new TimestampPrinter(timestamp))
        .add("node", memberId)
        .add("serviceName", serviceName)
        .add("serviceType", serviceType)
        .add("serviceConfig", ArraySizeHashPrinter.of(serviceConfig))
        .add("readConsistency", readConsistency)
        .add("minTimeout", minTimeout)
        .add("maxTimeout", maxTimeout)
        .toString();
  }
}
