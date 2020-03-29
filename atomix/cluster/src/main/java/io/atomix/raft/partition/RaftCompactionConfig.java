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
package io.atomix.raft.partition;

/** Raft compaction configuration. */
public class RaftCompactionConfig {

  private static final boolean DEFAULT_DYNAMIC_COMPACTION = true;
  private static final double DEFAULT_FREE_DISK_BUFFER = .2;
  private static final double DEFAULT_FREE_MEMORY_BUFFER = .2;

  private boolean dynamic = DEFAULT_DYNAMIC_COMPACTION;
  private double freeDiskBuffer = DEFAULT_FREE_DISK_BUFFER;
  private double freeMemoryBuffer = DEFAULT_FREE_MEMORY_BUFFER;

  /**
   * Returns the free disk buffer.
   *
   * @return the free disk buffer
   */
  public double getFreeDiskBuffer() {
    return freeDiskBuffer;
  }

  /**
   * Sets the free disk buffer.
   *
   * @param freeDiskBuffer the free disk buffer
   * @return the compaction configuration
   */
  public RaftCompactionConfig setFreeDiskBuffer(final double freeDiskBuffer) {
    this.freeDiskBuffer = freeDiskBuffer;
    return this;
  }

  /**
   * Returns the free memory buffer.
   *
   * @return the free memory buffer
   */
  public double getFreeMemoryBuffer() {
    return freeMemoryBuffer;
  }

  /**
   * Sets the free memory buffer.
   *
   * @param freeMemoryBuffer the free memory buffer
   * @return the compaction configuration
   */
  public RaftCompactionConfig setFreeMemoryBuffer(final double freeMemoryBuffer) {
    this.freeMemoryBuffer = freeMemoryBuffer;
    return this;
  }

  /**
   * Returns whether dynamic compaction is enabled.
   *
   * @return whether dynamic compaction is enabled
   */
  public boolean isDynamic() {
    return dynamic;
  }

  /**
   * Sets whether dynamic compaction is enabled.
   *
   * @param dynamic whether dynamic compaction is enabled
   * @return the compaction configuration
   */
  public RaftCompactionConfig setDynamic(final boolean dynamic) {
    this.dynamic = dynamic;
    return this;
  }
}
