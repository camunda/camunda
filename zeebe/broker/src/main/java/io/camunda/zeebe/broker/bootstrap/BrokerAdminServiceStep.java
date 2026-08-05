/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.zeebe.broker.system.management.BrokerAdminServiceImpl;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.DynamicConfigExportingStateController;
import io.camunda.zeebe.dynamic.config.api.ExportingStateController;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

/**
 * Sets up one {@link BrokerAdminServiceImpl} per physical tenant and registers each in the broker
 * startup context, so that node-level admin operations (partition status, pause/resume
 * processing/exporting, snapshots) can be scoped to a single physical tenant's partitions via the
 * {@code partitions} actuator's {@code physicalTenant} parameter.
 */
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

    final var tenantIds = brokerStartupContext.getPhysicalTenantIds().known();
    final var exportingStateController = createExportingStateController(brokerStartupContext);

    final var futures =
        tenantIds.stream()
            .map(
                tenantId ->
                    startTenantService(
                        brokerStartupContext,
                        tenantId,
                        exportingStateController,
                        concurrencyControl))
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        futures,
        (services, error) -> {
          if (error != null) {
            final var cleanupFutures =
                tenantIds.stream()
                    .map(id -> shutdownTenantService(brokerStartupContext, id, concurrencyControl))
                    .collect(new ActorFutureCollector<>(concurrencyControl));
            concurrencyControl.runOnCompletion(
                cleanupFutures,
                (ignored, cleanupError) -> startupFuture.completeExceptionally(error));
            return;
          }

          forwardExceptions(
              () -> {
                brokerStartupContext
                    .getSpringBrokerBridge()
                    .registerBrokerAdminServiceSupplier(
                        () ->
                            brokerStartupContext.getBrokerAdminService(DEFAULT_PHYSICAL_TENANT_ID));
                brokerStartupContext
                    .getSpringBrokerBridge()
                    .registerBrokerAdminServiceByTenantLookup(
                        brokerStartupContext::getBrokerAdminService);
                brokerStartupContext
                    .getSpringBrokerBridge()
                    .registerBrokerAdminServiceTenantIdsSupplier(() -> tenantIds);

                startupFuture.complete(brokerStartupContext);
              },
              startupFuture);
        });
  }

  /**
   * Builds the {@link ExportingStateController} that routes exporting changes through the dynamic
   * cluster configuration coordinator (the durable path). It is the same abstraction the actuator
   * and the v2 exporting API use, so all three share how a state change is submitted and awaited.
   */
  private ExportingStateController createExportingStateController(final BrokerStartupContext ctx) {
    final var configurationService = ctx.getClusterConfigurationService();
    final var requestSender =
        new ClusterConfigurationManagementRequestSender(
            ctx.getClusterServices().getCommunicationService(),
            ClusterConfigurationCoordinatorSupplier.from(
                configurationService::getCurrentClusterConfiguration),
            new ProtoBufSerializer());
    return new DynamicConfigExportingStateController(requestSender);
  }

  private ActorFuture<BrokerAdminServiceImpl> startTenantService(
      final BrokerStartupContext brokerStartupContext,
      final String physicalTenantId,
      final ExportingStateController exportingStateController,
      final ConcurrencyControl concurrencyControl) {

    final var adminService =
        new BrokerAdminServiceImpl(
            brokerStartupContext.getPartitionManagers().get(physicalTenantId),
            exportingStateController.getByTenant(physicalTenantId));

    final var result = concurrencyControl.<BrokerAdminServiceImpl>createFuture();
    final var submitActorFuture =
        brokerStartupContext.getActorSchedulingService().submitActor(adminService);

    concurrencyControl.runOnCompletion(
        submitActorFuture,
        (ok, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
            return;
          }

          brokerStartupContext.addBrokerAdminService(physicalTenantId, adminService);
          result.complete(adminService);
        });

    return result;
  }

  private ActorFuture<Void> shutdownTenantService(
      final BrokerStartupContext ctx,
      final String physicalTenantId,
      final ConcurrencyControl concurrencyControl) {

    final var adminService = ctx.getBrokerAdminService(physicalTenantId);
    if (adminService == null) {
      return CompletableActorFuture.completed(null);
    }

    ctx.removeBrokerAdminService(physicalTenantId);

    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.runOnCompletion(
        adminService.closeAsync(),
        (ok, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(null);
          }
        });
    return result;
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var tenantIds = brokerShutdownContext.getPhysicalTenantIds().known();

    final var futures =
        tenantIds.stream()
            .map(
                tenantId ->
                    shutdownTenantService(brokerShutdownContext, tenantId, concurrencyControl))
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        futures,
        (ok, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }
}
