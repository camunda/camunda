/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.broker.transport.streamapi.JobStreamApiServer;
import io.camunda.zeebe.stream.api.ActivatedJob;
import io.camunda.zeebe.stream.api.GatewayStreamer;
import io.camunda.zeebe.stream.api.JobActivationProperties;
import java.util.Objects;

public record JobStreamService(
    JobStreamApiServer server, GatewayStreamer<JobActivationProperties, ActivatedJob> jobStreamer) {

  public JobStreamService(
      final JobStreamApiServer server,
      final GatewayStreamer<JobActivationProperties, ActivatedJob> jobStreamer) {
    this.server = Objects.requireNonNull(server, "must provide a stream server");
    this.jobStreamer = Objects.requireNonNull(jobStreamer, "must provide a job streamer");
  }
}
