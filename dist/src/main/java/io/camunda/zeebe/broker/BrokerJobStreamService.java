/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.zeebe.broker.jobstream.JobStreamService;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.shared.management.JobStreamEndpoint;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamInfo;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(value = "jobStreamService")
final class BrokerJobStreamService implements JobStreamEndpoint.Service {
  private final SpringBrokerBridge bridge;

  @Autowired
  public BrokerJobStreamService(final SpringBrokerBridge bridge) {
    this.bridge = bridge;
  }

  @Override
  public Collection<RemoteStreamInfo<JobActivationProperties>> remoteJobStreams() {
    return bridge
        .getJobStreamService()
        .map(JobStreamService::remoteStreamService)
        .map(RemoteStreamService::streams)
        .orElse(Collections.emptyList());
  }

  @Override
  public Collection<ClientStream<JobActivationProperties>> clientJobStreams() {
    return bridge
        .getJobStreamClient()
        .map(JobStreamClient::list)
        .map(ActorFuture::join)
        .orElse(Collections.emptyList());
  }
}
