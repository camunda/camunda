/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;

final class BrokerAdminServiceStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Broker Admin Interface";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var adminService = new BrokerAdminServiceImpl();

    final var submitActorFuture =
        brokerStartupContext.getActorSchedulingService().submitActor(adminService);

    concurrencyControl.runOnCompletion(
        submitActorFuture,
        (ok, error) -> {
          if (error != null) {
            startupFuture.complete(brokerStartupContext);
            return;
          }

          forwardExceptions(
              () -> {
                brokerStartupContext
                    .getSpringBrokerBridge()
                    .registerBrokerAdminServiceSupplier(() -> adminService);

                brokerStartupContext.setBrokerAdminService(adminService);
                startupFuture.complete(brokerStartupContext);
              },
              startupFuture);
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var adminService = brokerShutdownContext.getBrokerAdminService();

    if (adminService == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }
    final var closeFuture = adminService.closeAsync();

    concurrencyControl.runOnCompletion(
        closeFuture,
        (ok, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
            return;
          }

          forwardExceptions(
              () -> {
                brokerShutdownContext.setBrokerAdminService(null);

                shutdownFuture.complete(brokerShutdownContext);
              },
              shutdownFuture);
        });
  }
}
