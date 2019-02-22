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
import io.zeebe.broker.logstreams.state.StateStorageFactory;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;

/** Service representing a partition. */
public class Partition implements Service<Partition> {
  public static final String PARTITION_NAME_FORMAT = "partition-%d";

  public static String getPartitionName(final int partitionId) {
    return String.format(PARTITION_NAME_FORMAT, partitionId);
  }

  private final Injector<LogStream> logStreamInjector = new Injector<>();

  private final Injector<StateStorageFactory> stateStorageFactoryInjector = new Injector<>();

  private final PartitionInfo info;

  private final RaftState state;

  private LogStream logStream;

  private StateStorageFactory stateStorageFactory;

  public Partition(final PartitionInfo partitionInfo, final RaftState state) {
    this.info = partitionInfo;
    this.state = state;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    logStream = logStreamInjector.getValue();
    stateStorageFactory = stateStorageFactoryInjector.getValue();
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

  public StateStorageFactory getStateStorageFactory() {
    return stateStorageFactory;
  }

  public Injector<StateStorageFactory> getStateStorageFactoryInjector() {
    return stateStorageFactoryInjector;
  }
}
