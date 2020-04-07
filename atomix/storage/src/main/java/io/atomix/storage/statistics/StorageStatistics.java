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
package io.atomix.storage.statistics;

import java.io.File;

/** Atomix storage statistics. */
public class StorageStatistics {

  private final File file;

  public StorageStatistics(final File file) {
    this.file = file;
  }

  /**
   * Returns the amount of usable space remaining.
   *
   * @return the amount of usable space remaining
   */
  public long getUsableSpace() {
    return file.getUsableSpace();
  }

  /**
   * Returns the amount of free space remaining.
   *
   * @return the amount of free space remaining
   */
  public long getFreeSpace() {
    return file.getFreeSpace();
  }

  /**
   * Returns the total amount of space.
   *
   * @return the total amount of space
   */
  public long getTotalSpace() {
    return file.getTotalSpace();
  }

  /**
   * Returns the amount of free memory remaining.
   *
   * @return the amount of free memory remaining if successful, -1 return indicates failure.
   */
  public long getFreeMemory() {
    return Runtime.getRuntime().freeMemory();
  }

  /**
   * Returns the total amount of memory.
   *
   * @return the total amount of memory if successful, -1 return indicates failure.
   */
  public long getTotalMemory() {
    return Runtime.getRuntime().totalMemory();
  }
}
