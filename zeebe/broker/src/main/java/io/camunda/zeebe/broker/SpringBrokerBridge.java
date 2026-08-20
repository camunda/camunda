/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Helper class that allows Spring beans to access information from the Broker code that is not
 * managed by Spring
 */
@Component
public class SpringBrokerBridge {

  private static final Logger LOG = LoggerFactory.getLogger(SpringBrokerBridge.class);

  private Supplier<BrokerHealthCheckService> healthCheckServiceSupplier;
  private Supplier<BrokerAdminService> adminServiceSupplier;
  private Function<String, BrokerAdminService> adminServiceByTenantLookup;
  private Supplier<Set<String>> adminServiceTenantIdsSupplier;
  private Supplier<Collection<JobStreamService>> jobStreamServicesSupplier;
  private Function<String, JobStreamService> jobStreamServiceByTenantLookup;
  private Supplier<JobStreamClient> jobStreamClientSupplier;

  private BiConsumer<Integer, String> shutdownHelper;

  public void registerBrokerHealthCheckServiceSupplier(
      final Supplier<BrokerHealthCheckService> healthCheckServiceSupplier) {
    this.healthCheckServiceSupplier = healthCheckServiceSupplier;
  }

  public Optional<BrokerHealthCheckService> getBrokerHealthCheckService() {
    return Optional.ofNullable(healthCheckServiceSupplier).map(Supplier::get);
  }

  public void registerBrokerAdminServiceSupplier(
      final Supplier<BrokerAdminService> adminServiceSupplier) {
    this.adminServiceSupplier = adminServiceSupplier;
  }

  public Optional<BrokerAdminService> getAdminService() {
    return Optional.ofNullable(adminServiceSupplier).map(Supplier::get);
  }

  /**
   * Registers a lookup function resolving the {@link BrokerAdminService} responsible for a given
   * physical tenant's partitions.
   */
  public void registerBrokerAdminServiceByTenantLookup(
      final Function<String, BrokerAdminService> adminServiceByTenantLookup) {
    this.adminServiceByTenantLookup = adminServiceByTenantLookup;
  }

  /** Returns the {@link BrokerAdminService} for the given physical tenant, if any. */
  public Optional<BrokerAdminService> getAdminService(final String physicalTenantId) {
    return Optional.ofNullable(adminServiceByTenantLookup)
        .flatMap(lookup -> Optional.ofNullable(lookup.apply(physicalTenantId)));
  }

  /**
   * Registers a supplier of every physical tenant ID that has a {@link BrokerAdminService}
   * registered, so that node-level operations without an explicit {@code physicalTenant} can be
   * applied across the whole node (all physical tenants) rather than defaulting to a single one.
   */
  public void registerBrokerAdminServiceTenantIdsSupplier(
      final Supplier<Set<String>> adminServiceTenantIdsSupplier) {
    this.adminServiceTenantIdsSupplier = adminServiceTenantIdsSupplier;
  }

  /** Returns every physical tenant ID that has a {@link BrokerAdminService} registered. */
  public Set<String> getBrokerAdminServiceTenantIds() {
    return Optional.ofNullable(adminServiceTenantIdsSupplier).map(Supplier::get).orElse(Set.of());
  }

  public void registerJobStreamClientSupplier(
      final Supplier<JobStreamClient> jobStreamClientSupplier) {
    this.jobStreamClientSupplier = jobStreamClientSupplier;
  }

  public Optional<JobStreamClient> getJobStreamClient() {
    return Optional.ofNullable(jobStreamClientSupplier).map(Supplier::get);
  }

  public void registerJobStreamServicesSupplier(
      final Supplier<Collection<JobStreamService>> jobStreamServicesSupplier) {
    this.jobStreamServicesSupplier = jobStreamServicesSupplier;
  }

  public Optional<Collection<JobStreamService>> getJobStreamServices() {
    return Optional.ofNullable(jobStreamServicesSupplier).map(Supplier::get);
  }

  /** Registers a lookup function resolving the {@link JobStreamService} for a given tenant. */
  public void registerJobStreamServiceByTenantLookup(
      final Function<String, JobStreamService> jobStreamServiceByTenantLookup) {
    this.jobStreamServiceByTenantLookup = jobStreamServiceByTenantLookup;
  }

  /** Returns the {@link JobStreamService} for the given physical tenant, if any. */
  public Optional<JobStreamService> getJobStreamService(final String physicalTenantId) {
    return Optional.ofNullable(jobStreamServiceByTenantLookup)
        .flatMap(lookup -> Optional.ofNullable(lookup.apply(physicalTenantId)));
  }

  /**
   * Registers a shutdown helper that can initiate a graceful shutdown of the broker. This will be
   * used when any exceptional cases may need to be handled by shutting down the broker.
   *
   * @param shutdownHelper the shutdown helper accepting an error code and a reason string
   */
  public void registerShutdownHelper(final BiConsumer<Integer, String> shutdownHelper) {
    this.shutdownHelper = shutdownHelper;
  }

  public void initiateShutdown(final int errorCode, final String reason) {
    LOG.warn("Initiating broker shutdown with error code {}: {}", errorCode, reason);
    if (shutdownHelper != null) {
      shutdownHelper.accept(errorCode, reason);
    } else {
      System.exit(errorCode);
    }
  }
}
