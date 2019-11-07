/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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

public class LogStorageAppenderService implements Service<LogStorageAppender> {
  private final Injector<Subscription> appenderSubscriptionInjector = new Injector<>();

  private final LogStorage logStorage;
  private final int partitionId;
  private final int maxAppendBlockSize;

  private LogStorageAppender service;

  public LogStorageAppenderService(final LogStorage logStorage, final int partitionId, final int maxAppendBlockSize) {
    this.logStorage = logStorage;
    this.partitionId = partitionId;
    this.maxAppendBlockSize = maxAppendBlockSize;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    final Subscription subscription = appenderSubscriptionInjector.getValue();

    service =
        new LogStorageAppender(
            startContext.getName(),
            partitionId,
            logStorage,
            subscription,
            maxAppendBlockSize);

    startContext.async(startContext.getScheduler().submitActor(service, SchedulingHints.ioBound()));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(service.close());
  }

  @Override
  public LogStorageAppender get() {
    return service;
  }

  public Injector<Subscription> getAppenderSubscriptionInjector() {
    return appenderSubscriptionInjector;
  }
}
