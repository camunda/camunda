/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import org.agrona.concurrent.SnowflakeIdGenerator;
import org.agrona.concurrent.SystemEpochClock;

public class RequestIdGeneratorStep implements StartupStep<BrokerStartupContext> {
  // Unix epoch time for January 1, 2023 1:00:00 AM GMT+01:00
  private static final long TIMESTAMP_OFFSET_2023 = 1672531200000L;

  @Override
  public String getName() {
    return "Request Id Generator";
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(
      final BrokerStartupContext brokerStartupContext) {
    final ActorFuture<BrokerStartupContext> started =
        brokerStartupContext.getConcurrencyControl().createFuture();

    final var brokerInfo = brokerStartupContext.getBrokerInfo();

    final var requestIdGenerator =
        new SnowflakeIdGenerator(
            SnowflakeIdGenerator.NODE_ID_BITS_DEFAULT,
            SnowflakeIdGenerator.SEQUENCE_BITS_DEFAULT,
            brokerInfo.getNodeId(),
            TIMESTAMP_OFFSET_2023,
            SystemEpochClock.INSTANCE);

    brokerStartupContext.setRequestIdGenerator(requestIdGenerator);
    started.complete(brokerStartupContext);
    return started;
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerStartupContext) {
    final ActorFuture<BrokerStartupContext> stopFuture =
        brokerStartupContext.getConcurrencyControl().createFuture();

    brokerStartupContext.setRequestIdGenerator(null);

    stopFuture.complete(brokerStartupContext);
    return stopFuture;
  }
}
