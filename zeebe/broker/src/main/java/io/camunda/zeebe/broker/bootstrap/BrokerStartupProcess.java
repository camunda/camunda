/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerStepMetrics;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupProcess;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class BrokerStartupProcess {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final StartupProcess<BrokerStartupContext> startupProcess;

  private BrokerStartupContext context;
  private final ConcurrencyControl concurrencyControl;

  public BrokerStartupProcess(final BrokerStartupContext brokerStartupContext) {
    concurrencyControl = brokerStartupContext.getConcurrencyControl();
    context = brokerStartupContext;

    final var brokerStepMetrics = new BrokerStepMetrics(context.getMeterRegistry());

    final var undecoratedSteps = buildStartupSteps(brokerStartupContext.getBrokerConfiguration());

    final var decoratedSteps =
        undecoratedSteps.stream()
            .map(step -> new BrokerStepMetricDecorator(brokerStepMetrics, step))
            .collect(Collectors.toList());
    startupProcess = new StartupProcess<>(LOG, decoratedSteps);
  }

  private List<StartupStep<BrokerStartupContext>> buildStartupSteps(final BrokerCfg config) {
    final var result = new ArrayList<StartupStep<BrokerStartupContext>>();
    /*result.add(
    new NodeIdMapperStartupStep(config.getLeaseConfig(), config.getCluster().getClusterSize()));*/
    result.add(new ClusterServicesStep());
    result.add(new ClusterConfigurationManagerStep());

    // must be executed before any disk space usage listeners are registered
    result.add(new DiskSpaceUsageMonitorStep());

    result.add(new MonitoringServerStep());

    result.add(new ApiMessagingServiceStep());
    result.add(new RequestIdGeneratorStep());
    result.add(new GatewayBrokerTransportStep());
    result.add(new CommandApiServiceStep());

    if (config.getGateway().isEnable()) {
      result.add(new EmbeddedGatewayServiceStep());
    }

    result.add(new JobStreamServiceStep());
    result.add(new SnapshotApiServiceStep());
    result.add(new PartitionManagerStep());
    result.add(new BrokerAdminServiceStep());

    return result;
  }

  public ActorFuture<BrokerContext> start() {
    final ActorFuture<BrokerContext> result = concurrencyControl.createFuture();

    final var startupFuture = startupProcess.startup(concurrencyControl, context);

    concurrencyControl.runOnCompletion(
        startupFuture,
        (bsc, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            context = bsc;
            result.complete(createBrokerContext(bsc));
          }
        });
    return result;
  }

  public ActorFuture<Void> stop() {
    final ActorFuture<Void> result = concurrencyControl.createFuture();

    final var shutdownFuture = startupProcess.shutdown(concurrencyControl, context);

    concurrencyControl.runOnCompletion(
        shutdownFuture,
        (bsc, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(null);
          }
        });
    return result;
  }

  private BrokerContext createBrokerContext(final BrokerStartupContext bsc) {
    return new BrokerContextImpl(
        bsc.getDiskSpaceUsageMonitor(),
        bsc.getClusterServices(),
        bsc.getEmbeddedGatewayService(),
        bsc.getPartitionManager(),
        bsc.getBrokerAdminService(),
        bsc.getApiMessagingService());
  }
}
