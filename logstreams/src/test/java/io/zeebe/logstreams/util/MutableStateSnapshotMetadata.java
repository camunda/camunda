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
package io.zeebe.logstreams.util;

import io.zeebe.logstreams.state.StateSnapshotMetadata;

public class MutableStateSnapshotMetadata extends StateSnapshotMetadata {
  private long lastSuccessfulProcessedEventPosition;
  private long lastWrittenEventPosition;
  private int lastWrittenEventTerm;
  private boolean exists;

  public MutableStateSnapshotMetadata(
      long lastSuccessfulProcessedEventPosition,
      long lastWrittenEventPosition,
      int lastWrittenEventTerm,
      boolean exists) {
    super(
        lastSuccessfulProcessedEventPosition,
        lastWrittenEventPosition,
        lastWrittenEventTerm,
        exists);
    this.lastSuccessfulProcessedEventPosition = lastSuccessfulProcessedEventPosition;
    this.lastWrittenEventPosition = lastWrittenEventPosition;
    this.lastWrittenEventTerm = lastWrittenEventTerm;
    this.exists = exists;
  }

  @Override
  public long getLastSuccessfulProcessedEventPosition() {
    return lastSuccessfulProcessedEventPosition;
  }

  public void setLastSuccessfulProcessedEventPosition(long lastSuccessfulProcessedEventPosition) {
    this.lastSuccessfulProcessedEventPosition = lastSuccessfulProcessedEventPosition;
  }

  @Override
  public long getLastWrittenEventPosition() {
    return lastWrittenEventPosition;
  }

  public void setLastWrittenEventPosition(long lastWrittenEventPosition) {
    this.lastWrittenEventPosition = lastWrittenEventPosition;
  }

  @Override
  public int getLastWrittenEventTerm() {
    return lastWrittenEventTerm;
  }

  public void setLastWrittenEventTerm(int lastWrittenEventTerm) {
    this.lastWrittenEventTerm = lastWrittenEventTerm;
  }

  @Override
  public boolean exists() {
    return exists;
  }

  public void setExists(boolean exists) {
    this.exists = exists;
  }
}
