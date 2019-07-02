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
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.util.collection.Tuple;
import java.util.function.Supplier;

public class ControllableSnapshotRestoreContext implements SnapshotRestoreContext {

  private StateStorage processorStateStorage;
  private Supplier<Tuple<Long, Long>> positionSupplier;

  public void setProcessorStateStorage(StateStorage processorStateStorage) {
    this.processorStateStorage = processorStateStorage;
  }

  public void setPositionSupplier(Supplier<Tuple<Long, Long>> exporterPositionSupplier) {
    this.positionSupplier = exporterPositionSupplier;
  }

  @Override
  public StateStorage getStateStorage() {
    return processorStateStorage;
  }

  @Override
  public Supplier<Tuple<Long, Long>> getSnapshotPositionSupplier() {
    return positionSupplier;
  }

  public void reset() {
    processorStateStorage = null;
    positionSupplier = null;
  }
}
