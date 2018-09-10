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

import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ActorScheduler;
import java.util.function.Function;

public class FsLogStorageService implements Service<LogStorage> {
  private final FsLogStorageConfiguration config;
  private final int partitionId;
  private final Function<FsLogStorage, FsLogStorage> logStorageStubber; // for testing only

  private FsLogStorage logStorage;

  public FsLogStorageService(
      final FsLogStorageConfiguration config,
      final int partitionId,
      final Function<FsLogStorage, FsLogStorage> logStorageStubber) {
    this.config = config;
    this.partitionId = partitionId;
    this.logStorageStubber = logStorageStubber;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final ActorScheduler scheduler = startContext.getScheduler();
    logStorage =
        logStorageStubber.apply(
            new FsLogStorage(config, scheduler.getMetricsManager(), partitionId));

    startContext.run(logStorage::open);
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.run(logStorage::close);
  }

  @Override
  public LogStorage get() {
    return logStorage;
  }
}
