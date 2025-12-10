/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.engine.processing.streamprocessor.ProcessInstanceStreamer;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import java.util.Objects;
import java.util.stream.Stream;

public record ProcessInstanceStreamService(
    RemoteStreamService<Long, ProcessInstanceRecord> remoteStreamService,
    ProcessInstanceStreamer processInstanceStreamer) {

  public ProcessInstanceStreamService(
      final RemoteStreamService<Long, ProcessInstanceRecord> remoteStreamService,
      final ProcessInstanceStreamer processInstanceStreamer) {
    this.remoteStreamService =
        Objects.requireNonNull(remoteStreamService, "must provide a stream remoteStreamService");
    this.processInstanceStreamer =
        Objects.requireNonNull(processInstanceStreamer, "must provide a job streamer");
  }

  public ActorFuture<?> closeAsync(final ConcurrencyControl executor) {
    return Stream.of(remoteStreamService.closeAsync(executor))
        .collect(new ActorFutureCollector<>(executor));
  }
}
