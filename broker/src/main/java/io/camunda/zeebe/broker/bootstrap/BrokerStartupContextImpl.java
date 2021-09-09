/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;

public final class BrokerStartupContextImpl implements BrokerStartupContext {

  private final BrokerInfo brokerInfo;
  private final SpringBrokerBridge springBrokerBridge;
  private final ConcurrencyControl concurrencyControl;
  private final ActorSchedulingService actorSchedulingService;
  private final List<PartitionListener> partitionListeners = new ArrayList<>();

  private final BrokerHealthCheckService healthCheckService;

  public BrokerStartupContextImpl(
      final BrokerInfo brokerInfo,
      final SpringBrokerBridge springBrokerBridge,
      final ConcurrencyControl concurrencyControl,
      final ActorSchedulingService actorSchedulingService,
      final BrokerHealthCheckService healthCheckService) {

    this.brokerInfo = requireNonNull(brokerInfo);
    this.springBrokerBridge = requireNonNull(springBrokerBridge);
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.actorSchedulingService = requireNonNull(actorSchedulingService);
    this.healthCheckService = requireNonNull(healthCheckService);
  }

  @Override
  public BrokerInfo getBrokerInfo() {
    return brokerInfo;
  }

  @Override
  public SpringBrokerBridge getSpringBrokerBridge() {
    return springBrokerBridge;
  }

  @Override
  public ConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }

  @Override
  public ActorFuture<Void> scheduleActor(final Actor actor) {
    return actorSchedulingService.submitActor(actor);
  }

  @Override
  public BrokerHealthCheckService getHealthCheckService() {
    return healthCheckService;
  }

  @Override
  public void addPartitionListener(final PartitionListener listener) {
    partitionListeners.add(requireNonNull(listener));
  }

  @Override
  public void removePartitionListener(final PartitionListener listener) {
    partitionListeners.remove(requireNonNull(listener));
  }

  @Override
  public List<PartitionListener> getPartitionListeners() {
    return unmodifiableList(partitionListeners);
  }
}
