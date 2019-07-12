/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer;

import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ServiceContainer {
  void start();

  ActorFuture<Boolean> hasService(ServiceName<?> name);

  <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);

  CompositeServiceBuilder createComposite(ServiceName<Void> name);

  ActorFuture<Void> removeService(ServiceName<?> serviceName);

  void close(long awaitTime, TimeUnit timeUnit)
      throws TimeoutException, ExecutionException, InterruptedException;

  ActorFuture<Void> closeAsync();
}
