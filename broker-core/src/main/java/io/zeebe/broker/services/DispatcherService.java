/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.services;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.DispatcherBuilder;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class DispatcherService implements Service<Dispatcher> {
  protected DispatcherBuilder dispatcherBuilder;
  protected Dispatcher dispatcher;

  public DispatcherService(DispatcherBuilder builder) {
    this.dispatcherBuilder = builder;
  }

  @Override
  public void start(ServiceStartContext ctx) {
    dispatcher = dispatcherBuilder.name(ctx.getName()).actorScheduler(ctx.getScheduler()).build();
  }

  @Override
  public void stop(ServiceStopContext ctx) {
    ctx.async(dispatcher.closeAsync());
  }

  @Override
  public Dispatcher get() {
    return dispatcher;
  }
}
