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
package io.zeebe.distributedlog.restore.snapshot;

import io.zeebe.logstreams.state.StateStorage;
import java.util.function.Supplier;

public interface SnapshotRestoreContext {

  /** @return state storage of processor */
  StateStorage getStateStorage(int partitionId);

  /** @return a supplier that supplies the latest exported position by reading exporterStorage */
  Supplier<Long> getExporterPositionSupplier(int partitionId);

  /**
   * @return a supplier that supplies the latest processed position by reading snapshots in the
   *     processorStorage
   */
  Supplier<Long> getProcessorPositionSupplier(int partitionId);
}
