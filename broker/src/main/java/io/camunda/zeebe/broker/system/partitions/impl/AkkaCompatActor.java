/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AkkaCompatActor extends Actor {
  public <T, U> void waitOnActor(
      final Callable<ActorFuture<T>> callable,
      final ActorRef<U> replyTo,
      final BiFunction<T, Exception, U> adapt) {
    actor.run(
        () -> {
          final ActorFuture<T> f;
          try {
            f = callable.call();
          } catch (final Exception e) {
            replyTo.tell(adapt.apply(null, e));
            return;
          }
          f.onComplete((result, error) -> replyTo.tell(adapt.apply(result, null)));
        });
  }

  public <T, B> void onActor(
      final ActorContext<B> ctx,
      final Callable<ActorFuture<T>> callable,
      final Function<T, B> transformResult,
      final Function<Throwable, B> transformError) {
    actor.run(
        () -> {
          try {
            callable
                .call()
                .onComplete(
                    (result, error) -> {
                      // ctx.getLog().info("ActorScheduler future is complete: {}", result);
                      if (error != null) {
                        ctx.getSelf().tell(transformError.apply(error));
                      } else {
                        ctx.getSelf().tell(transformResult.apply(result));
                      }
                    });
          } catch (final Exception e) {
            ctx.getSelf().tell(transformError.apply(e));
          }
        });
  }
}
