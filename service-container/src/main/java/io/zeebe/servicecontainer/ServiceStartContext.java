/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer;

import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;

public interface ServiceStartContext extends ServiceContext {
  String getName();

  ServiceName<?> getServiceName();

  <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);

  CompositeServiceBuilder createComposite(ServiceName<Void> name);

  <S> ActorFuture<Void> removeService(ServiceName<S> name);

  <S> ActorFuture<Boolean> hasService(ServiceName<S> name);

  ActorScheduler getScheduler();

  void async(ActorFuture<?> future, boolean interruptible);

  @Override
  default void async(ActorFuture<?> future) {
    async(future, false);
  }
}
