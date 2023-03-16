/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import java.util.Objects;

public class JobStreamService implements AsyncClosable {

  private final RemoteStreamService<JobActivationProperties, ActivatedJob> server;
  private final GatewayStreamer<JobActivationProperties, ActivatedJob> jobStreamer;

  public JobStreamService(
      final RemoteStreamService<JobActivationProperties, ActivatedJob> server,
      final GatewayStreamer<JobActivationProperties, ActivatedJob> jobStreamer) {
    this.server = Objects.requireNonNull(server, "must provide a stream server");
    this.jobStreamer = Objects.requireNonNull(jobStreamer, "must provide a job streamer");
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    return server.closeAsync();
  }

  public GatewayStreamer<JobActivationProperties, ActivatedJob> jobStreamer() {
    return jobStreamer;
  }
}
