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
package io.zeebe.logstreams.impl;

import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.SchedulingHints;
import io.zeebe.util.sched.channel.ActorConditions;

public class LogStorageCommitListenerService implements Service<LogStorageCommitListener> {

  private final Injector<DistributedLogstreamPartition> distributedLogstreamInjector =
      new Injector<>();
  private final LogStream logStream;

  private LogStorageCommitListener logStorageCommitListener;
  private final ActorConditions onLogStorageAppendedConditions;

  public LogStorageCommitListenerService(
      LogStream logStream, ActorConditions onLogStorageAppendedConditions) {
    this.logStream = logStream;
    this.onLogStorageAppendedConditions = onLogStorageAppendedConditions;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    this.logStorageCommitListener =
        new LogStorageCommitListener(
            logStream.getLogStorage(),
            logStream,
            distributedLogstreamInjector.getValue(),
            onLogStorageAppendedConditions);

    startContext.async(
        startContext
            .getScheduler()
            .submitActor(logStorageCommitListener, true, SchedulingHints.ioBound()));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(logStorageCommitListener.close());
  }

  @Override
  public LogStorageCommitListener get() {
    return this.logStorageCommitListener;
  }

  public Injector<DistributedLogstreamPartition> getDistributedLogstreamInjector() {
    return distributedLogstreamInjector;
  }
}
