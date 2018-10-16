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

import io.zeebe.logstreams.impl.LogBlockIndexWriter;
import io.zeebe.logstreams.impl.LogStreamBuilder;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.SchedulingHints;

public class LogBlockIndexWriterService implements Service<LogBlockIndexWriter> {
  private final Injector<LogBlockIndex> logBlockIndexInjector = new Injector<>();
  private final Injector<LogStorage> logStorageInjector = new Injector<>();

  private LogBlockIndexWriter logBlockIndexWriter;
  private LogStreamBuilder logStreamBuilder;

  public LogBlockIndexWriterService(LogStreamBuilder logStreamBuilder) {
    this.logStreamBuilder = logStreamBuilder;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final LogBlockIndex logBlockIndex = logBlockIndexInjector.getValue();
    final LogStorage logStorage = logStorageInjector.getValue();
    final ActorScheduler scheduler = startContext.getScheduler();

    logBlockIndexWriter =
        new LogBlockIndexWriter(
            startContext.getName(),
            logStreamBuilder,
            logStorage,
            logBlockIndex,
            scheduler.getMetricsManager());

    startContext.async(scheduler.submitActor(logBlockIndexWriter, true, SchedulingHints.ioBound()));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(logBlockIndexWriter.closeAsync());
  }

  @Override
  public LogBlockIndexWriter get() {
    return logBlockIndexWriter;
  }

  public Injector<LogBlockIndex> getLogBlockIndexInjector() {
    return logBlockIndexInjector;
  }

  public Injector<LogStorage> getLogStorageInjector() {
    return logStorageInjector;
  }
}
