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
package io.zeebe.distributedlog;

import java.io.File;

/**
 * Represents the storage configuration of a partition. It keeps the path of the local data
 * directory for
 *
 * <ul>
 *   <li>logstream segments
 *   <li>log block index
 *   <li>log block index snapshots
 *   <li>stream processor state
 * </ul>
 */
public class StorageConfiguration {

  private final File logDirectory;
  private final File statesDirectory;
  private int partitionId;
  private long logSegmentSize;

  public StorageConfiguration(final File partitionLogDir, final File statesDir) {
    this.logDirectory = partitionLogDir;
    this.statesDirectory = statesDir;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public File getLogDirectory() {
    return logDirectory;
  }

  public StorageConfiguration setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public StorageConfiguration setLogSegmentSize(final long value) {
    logSegmentSize = value;
    return this;
  }

  public long getLogSegmentSize() {
    return logSegmentSize;
  }

  public File getStatesDirectory() {
    return statesDirectory;
  }
}
