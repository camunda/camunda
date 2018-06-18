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
package io.zeebe.broker.clustering.base.partitions;

import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.*;

/** Service representing a partition. */
public class Partition implements Service<Partition> {
  private final Injector<LogStream> logStreamInjector = new Injector<>();

  private final Injector<SnapshotStorage> snapshotStorageInjector = new Injector<>();

  private final PartitionInfo info;

  private final RaftState state;

  private LogStream logStream;

  private SnapshotStorage snapshotStorage;

  public Partition(PartitionInfo partitionInfo, RaftState state) {
    this.info = partitionInfo;
    this.state = state;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    logStream = logStreamInjector.getValue();
    snapshotStorage = snapshotStorageInjector.getValue();
  }

  @Override
  public Partition get() {
    return this;
  }

  public PartitionInfo getInfo() {
    return info;
  }

  public RaftState getState() {
    return state;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public Injector<LogStream> getLogStreamInjector() {
    return logStreamInjector;
  }

  public SnapshotStorage getSnapshotStorage() {
    return snapshotStorage;
  }

  public Injector<SnapshotStorage> getSnapshotStorageInjector() {
    return snapshotStorageInjector;
  }
}
