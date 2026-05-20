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
import java.util.Optional;
import java.util.function.BiConsumer;
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
  private Supplier<JobStreamService> jobStreamServiceSupplier;
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

  public void registerJobStreamClientSupplier(
      final Supplier<JobStreamClient> jobStreamClientSupplier) {
    this.jobStreamClientSupplier = jobStreamClientSupplier;
  }

  public Optional<JobStreamClient> getJobStreamClient() {
    return Optional.ofNullable(jobStreamClientSupplier).map(Supplier::get);
  }

  public void registerJobStreamServiceSupplier(
      final Supplier<JobStreamService> jobStreamServiceSupplier) {
    this.jobStreamServiceSupplier = jobStreamServiceSupplier;
  }

  public Optional<JobStreamService> getJobStreamService() {
    return Optional.ofNullable(jobStreamServiceSupplier).map(Supplier::get);
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
