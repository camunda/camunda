/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClientImpl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@VisibleForTesting
@Component
public final class JobStreamComponent {

  @VisibleForTesting
  @Bean(destroyMethod = "close")
  public JobStreamClient jobStreamClient(
      final ActorScheduler scheduler,
      final AtomixCluster cluster,
      final MeterRegistry meterRegistry) {
    return new JobStreamClientImpl(scheduler, cluster.getCommunicationService(), meterRegistry);
  }
}
