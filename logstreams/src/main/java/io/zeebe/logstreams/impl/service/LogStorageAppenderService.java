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

import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.SchedulingHints;
import io.zeebe.util.sched.channel.ActorConditions;

public class LogStorageAppenderService implements Service<LogStorageAppender> {
  private final Injector<LogStorage> logStorageInjector = new Injector<>();
  private final Injector<Subscription> appenderSubscriptionInjector = new Injector<>();

  private final int maxAppendBlockSize;

  private LogStorageAppender service;
  private ActorConditions onLogStorageAppendedConditions;

  public LogStorageAppenderService(
      ActorConditions onLogStorageAppendedConditions, int maxAppendBlockSize) {
    this.onLogStorageAppendedConditions = onLogStorageAppendedConditions;
    this.maxAppendBlockSize = maxAppendBlockSize;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final LogStorage logStorage = logStorageInjector.getValue();
    final Subscription subscription = appenderSubscriptionInjector.getValue();

    service =
        new LogStorageAppender(
            startContext.getName(),
            logStorage,
            subscription,
            maxAppendBlockSize,
            onLogStorageAppendedConditions);

    startContext.async(
        startContext.getScheduler().submitActor(service, true, SchedulingHints.ioBound()));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(service.close());
  }

  @Override
  public LogStorageAppender get() {
    return service;
  }

  public Injector<LogStorage> getLogStorageInjector() {
    return logStorageInjector;
  }

  public Injector<Subscription> getAppenderSubscriptionInjector() {
    return appenderSubscriptionInjector;
  }
}
