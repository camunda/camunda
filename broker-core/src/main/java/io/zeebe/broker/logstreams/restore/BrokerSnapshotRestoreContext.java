/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.restore;

import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.distributedlog.restore.snapshot.SnapshotRestoreContext;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.util.collection.Tuple;
import java.util.function.Supplier;

public class BrokerSnapshotRestoreContext implements SnapshotRestoreContext {

  private final StatePositionSupplier positionSupplier;
  private final StateStorage restoreStateStorage;

  public BrokerSnapshotRestoreContext(
      StatePositionSupplier positionSupplier, StateStorage restoreStateStorage) {
    this.positionSupplier = positionSupplier;
    this.restoreStateStorage = restoreStateStorage;
  }

  @Override
  public StateStorage getStateStorage() {
    return this.restoreStateStorage;
  }

  @Override
  public Supplier<Tuple<Long, Long>> getSnapshotPositionSupplier() {
    return () -> positionSupplier.getLatestPositions();
  }
}
