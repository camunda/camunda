/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.service;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.ActorFuture;

public class LogWriteBufferSubscriptionService implements Service<Subscription> {
  private final Injector<Dispatcher> logWritebufferInjector = new Injector<>();

  private final String subscriptionName;

  private ActorFuture<Subscription> subscriptionFuture;

  public LogWriteBufferSubscriptionService(String subscriptionName) {
    this.subscriptionName = subscriptionName;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final Dispatcher logBuffer = logWritebufferInjector.getValue();

    subscriptionFuture = logBuffer.openSubscriptionAsync(subscriptionName);
    startContext.async(subscriptionFuture);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    final Dispatcher logBuffer = logWritebufferInjector.getValue();

    stopContext.async(logBuffer.closeSubscriptionAsync(subscriptionFuture.join()));
  }

  @Override
  public Subscription get() {
    return subscriptionFuture.join();
  }

  public Injector<Dispatcher> getWritebufferInjector() {
    return logWritebufferInjector;
  }
}
