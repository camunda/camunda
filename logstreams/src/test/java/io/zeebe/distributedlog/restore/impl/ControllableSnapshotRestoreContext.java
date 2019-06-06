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
package io.zeebe.distributedlog.restore.impl;

import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import io.zeebe.logstreams.state.SnapshotReplication;
import io.zeebe.logstreams.state.StateStorage;
import java.util.function.Supplier;

public class ControllableSnapshotRestoreContext implements SnapshotRestoreContext {

  private SnapshotReplication processorSnapshotReplicationConsumer;
  private StateStorage processorStateStorage;
  private Supplier<Long> exporterPositionSupplier;
  private Supplier<Long> processorPositionSupplier;

  public void setProcessorSnapshotReplicationConsumer(
      SnapshotReplication processorSnapshotReplicationConsumer) {
    this.processorSnapshotReplicationConsumer = processorSnapshotReplicationConsumer;
  }

  public void setProcessorStateStorage(StateStorage processorStateStorage) {
    this.processorStateStorage = processorStateStorage;
  }

  public void setExporterPositionSupplier(Supplier<Long> exporterPositionSupplier) {
    this.exporterPositionSupplier = exporterPositionSupplier;
  }

  public void setProcessorPositionSupplier(Supplier<Long> processorPositionSupplier) {
    this.processorPositionSupplier = processorPositionSupplier;
  }

  @Override
  public SnapshotReplication createSnapshotReplicationConsumer(int partitionId) {
    return processorSnapshotReplicationConsumer;
  }

  @Override
  public StateStorage getStateStorage(int partitionId) {
    return processorStateStorage;
  }

  @Override
  public Supplier<Long> getExporterPositionSupplier(StateStorage exporterStorage) {
    return exporterPositionSupplier;
  }

  @Override
  public Supplier<Long> getProcessorPositionSupplier(
      int partitionId, StateStorage processorStorage) {
    return processorPositionSupplier;
  }
}
