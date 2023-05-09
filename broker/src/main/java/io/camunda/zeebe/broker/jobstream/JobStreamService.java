/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.engine.processing.streamprocessor.ActivatedJob;
import io.camunda.zeebe.engine.processing.streamprocessor.JobActivationProperties;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import java.util.Objects;
import java.util.stream.Stream;

public record JobStreamService(
    RemoteStreamService<JobActivationProperties, ActivatedJob> remoteStreamService,
    JobStreamer jobStreamer,
    RemoteJobStreamErrorHandlerService errorHandlerService) {

  public JobStreamService(
      final RemoteStreamService<JobActivationProperties, ActivatedJob> remoteStreamService,
      final JobStreamer jobStreamer,
      final RemoteJobStreamErrorHandlerService errorHandlerService) {
    this.remoteStreamService =
        Objects.requireNonNull(remoteStreamService, "must provide a stream remoteStreamService");
    this.jobStreamer = Objects.requireNonNull(jobStreamer, "must provide a job streamer");
    this.errorHandlerService =
        Objects.requireNonNull(errorHandlerService, "must provide an error handler service");
  }

  public ActorFuture<?> closeAsync(final ConcurrencyControl executor) {
    return Stream.of(remoteStreamService.closeAsync(executor), errorHandlerService.closeAsync())
        .collect(new ActorFutureCollector<>(executor));
  }
}
