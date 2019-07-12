/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.service;

import io.zeebe.dispatcher.Subscription;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.SchedulingHints;

public class LogStorageAppenderService implements Service<LogStorageAppender> {
  private final Injector<Subscription> appenderSubscriptionInjector = new Injector<>();
  private final Injector<DistributedLogstreamPartition> distributedLogstreamInjector =
      new Injector<>();

  private final int maxAppendBlockSize;

  private LogStorageAppender service;

  public LogStorageAppenderService(int maxAppendBlockSize) {
    this.maxAppendBlockSize = maxAppendBlockSize;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final Subscription subscription = appenderSubscriptionInjector.getValue();

    service =
        new LogStorageAppender(
            startContext.getName(),
            distributedLogstreamInjector.getValue(),
            subscription,
            maxAppendBlockSize);

    startContext.async(startContext.getScheduler().submitActor(service, SchedulingHints.ioBound()));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(service.close());
  }

  @Override
  public LogStorageAppender get() {
    return service;
  }

  public Injector<Subscription> getAppenderSubscriptionInjector() {
    return appenderSubscriptionInjector;
  }

  public Injector<DistributedLogstreamPartition> getDistributedLogstreamInjector() {
    return distributedLogstreamInjector;
  }
}
