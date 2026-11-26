/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.actor;

import io.camunda.application.commons.actor.ActorIdleStrategyConfiguration.IdleStrategySupplier;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.broker.partitioning.PhysicalTenantActorSchedulers;
import io.camunda.zeebe.broker.system.configuration.TenantActorPoolCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorScheduler.DefaultActorThreadFactory;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@VisibleForTesting
public final class ActorSchedulerConfiguration {

  private static final String GATEWAY_THREAD_NAME_PREFIX = "gateway-";

  private final SchedulerConfiguration schedulerConfiguration;
  private final IdleStrategySupplier idleStrategySupplier;
  private final ActorClockConfiguration actorClockConfiguration;
  private final MeterRegistry registry;

  @Autowired
  public ActorSchedulerConfiguration(
      final SchedulerConfiguration schedulerConfiguration,
      final IdleStrategySupplier idleStrategySupplier,
      final ActorClockConfiguration actorClockConfiguration,
      final MeterRegistry registry) {
    this.schedulerConfiguration = schedulerConfiguration;
    this.idleStrategySupplier = idleStrategySupplier;
    this.actorClockConfiguration = actorClockConfiguration;
    this.registry = registry;
  }

  @Bean(destroyMethod = "close")
  public ActorScheduler scheduler() {
    final var scheduler =
        newScheduler(schedulerConfiguration.schedulerPrefix())
            .setCpuBoundActorThreadCount(schedulerConfiguration.cpuThreads())
            .setIoBoundActorThreadCount(schedulerConfiguration.ioThreads())
            .build();
    scheduler.start();
    return scheduler;
  }

  @Bean(destroyMethod = "close")
  public GatewayActorScheduler gatewayActorScheduler(final ActorScheduler scheduler) {
    final var gatewayCpuThreads = schedulerConfiguration.gatewayCpuThreads();
    if (gatewayCpuThreads <= 0) {
      return GatewayActorScheduler.shared(scheduler);
    }

    final var gatewayScheduler =
        newScheduler("Gateway")
            .setCpuBoundActorThreadCount(gatewayCpuThreads)
            .setIoBoundActorThreadCount(0)
            .setActorThreadFactory(new DefaultActorThreadFactory(GATEWAY_THREAD_NAME_PREFIX))
            .build();
    gatewayScheduler.start();
    return GatewayActorScheduler.dedicated(gatewayScheduler);
  }

  /**
   * The schedulers of the physical tenants. With the tenant pool disabled, all tenants use the
   * scheduler of the broker. Otherwise each tenant gets a scheduler of its own, named after the
   * broker-wide one and the tenant, when its first partition actor is submitted.
   */
  @Bean
  public PhysicalTenantActorSchedulers physicalTenantActorSchedulers(
      final ActorScheduler scheduler, final PhysicalTenantResolver physicalTenantResolver) {
    if (!schedulerConfiguration.tenantActorPool().enabled()) {
      return PhysicalTenantActorSchedulers.shared(scheduler);
    }

    return PhysicalTenantActorSchedulers.dedicated(
        physicalTenantId -> {
          final var tenantActorPool =
              physicalTenantResolver.forPhysicalTenant(physicalTenantId).getSystem().getActorPool();
          tenantActorPool.validate();
          return newScheduler(schedulerConfiguration.schedulerPrefix())
              .setSchedulerName(
                  String.format(
                      "%s-%s-%s",
                      schedulerConfiguration.schedulerPrefix(),
                      schedulerConfiguration.nodeId(),
                      physicalTenantId))
              .setCpuBoundActorThreadCount(tenantActorPool.getCpuThreadCount())
              .setIoBoundActorThreadCount(tenantActorPool.getIoThreadCount())
              .setActorThreadFactory(new DefaultActorThreadFactory(physicalTenantId + "-"))
              .build();
        });
  }

  private ActorScheduler.ActorSchedulerBuilder newScheduler(final String prefix) {
    return ActorScheduler.newActorScheduler()
        .setActorClock(actorClockConfiguration.getClock().orElse(null))
        .setMeterRegistry(schedulerConfiguration.metricsEnabled() ? registry : null)
        .setSchedulerName(String.format("%s-%s", prefix, schedulerConfiguration.nodeId()))
        .setIdleStrategySupplier(idleStrategySupplier);
  }

  /**
   * @param gatewayCpuThreads the CPU threads of a separate scheduler for the gateway side, see
   *     {@link GatewayActorScheduler}. With 0, the gateway side uses the scheduler of this
   *     configuration.
   * @param tenantActorPool the settings of the schedulers of the physical tenants, see {@link
   *     PhysicalTenantActorSchedulers}
   */
  public record SchedulerConfiguration(
      int cpuThreads,
      int ioThreads,
      int gatewayCpuThreads,
      boolean metricsEnabled,
      String schedulerPrefix,
      String nodeId,
      TenantActorPoolCfg tenantActorPool) {}
}
