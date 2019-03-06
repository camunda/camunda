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
package io.zeebe.distributedlog.impl;

import io.atomix.core.Atomix;
import io.atomix.protocols.raft.MultiRaftProtocol;
import io.zeebe.distributedlog.DistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstreamBuilder;
import io.zeebe.distributedlog.DistributedLogstreamType;
import io.zeebe.distributedlog.LogEventListener;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedLogstreamPartition implements Service<DistributedLogstreamPartition> {
  private static final Logger LOG = LoggerFactory.getLogger(DistributedLogstreamPartition.class);

  private DistributedLogstream distributedLog;

  private final String partitionName;
  private final String primitiveName;
  private Atomix atomix;
  private final Injector<Atomix> atomixInjector = new Injector<>();

  private static final MultiRaftProtocol PROTOCOL =
      MultiRaftProtocol.builder()
          // Maps partitionName to partitionId
          .withPartitioner(DistributedLogstreamName.getInstance())
          .build();

  public DistributedLogstreamPartition(int partitionId) {
    primitiveName = String.format("log-partition-%d", partitionId);
    partitionName = DistributedLogstreamName.getPartitionKey(partitionId);
  }

  public void append(ByteBuffer blockBuffer, long commitPosition) {
    distributedLog.append(partitionName, commitPosition, blockBuffer);
  }

  public void addListener(LogEventListener listener) {
    distributedLog.addListener(partitionName, listener);
  }

  public void removeListener(LogEventListener listener) {
    distributedLog.removeListener(partitionName, listener);
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.atomix = atomixInjector.getValue();

    distributedLog =
        atomix
            .<DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream>
                primitiveBuilder(primitiveName, DistributedLogstreamType.instance())
            .withProtocol(PROTOCOL)
            .build();
  }

  @Override
  public DistributedLogstreamPartition get() {
    return this;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
