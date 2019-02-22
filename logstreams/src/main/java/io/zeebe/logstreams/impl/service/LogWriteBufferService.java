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
package io.zeebe.logstreams.impl.service;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.DispatcherBuilder;
import io.zeebe.dispatcher.impl.PositionUtil;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class LogWriteBufferService implements Service<Dispatcher> {
  private final Injector<LogStorage> logStorageInjector = new Injector<>();
  private final Injector<LogBlockIndex> logBlockIndexInjector = new Injector<>();

  protected DispatcherBuilder dispatcherBuilder;
  protected Dispatcher dispatcher;

  public LogWriteBufferService(DispatcherBuilder builder) {
    this.dispatcherBuilder = builder;
  }

  @Override
  public void start(ServiceStartContext ctx) {
    ctx.run(
        () -> {
          final int partitionId = determineInitialPartitionId();

          dispatcher =
              dispatcherBuilder
                  .initialPartitionId(partitionId + 1)
                  .name(ctx.getName())
                  .actorScheduler(ctx.getScheduler())
                  .build();
        });
  }

  @Override
  public void stop(ServiceStopContext ctx) {
    ctx.async(dispatcher.closeAsync());
  }

  private int determineInitialPartitionId() {
    final LogStorage logStorage = logStorageInjector.getValue();
    final LogBlockIndex logBlockIndex = logBlockIndexInjector.getValue();

    try (BufferedLogStreamReader logReader = new BufferedLogStreamReader()) {
      logReader.wrap(logStorage, logBlockIndex);

      long lastPosition = 0;

      // Get position of last entry
      logReader.seekToLastEvent();
      if (logReader.hasNext()) {
        final LoggedEvent lastEntry = logReader.next();
        lastPosition = lastEntry.getPosition();
      }

      // dispatcher needs to generate positions greater than the last position
      int partitionId = 0;

      if (lastPosition > 0) {
        partitionId = PositionUtil.partitionId(lastPosition);
      }

      return partitionId;
    }
  }

  @Override
  public Dispatcher get() {
    return dispatcher;
  }

  public Injector<LogBlockIndex> getLogBlockIndexInjector() {
    return logBlockIndexInjector;
  }

  public Injector<LogStorage> getLogStorageInjector() {
    return logStorageInjector;
  }
}
