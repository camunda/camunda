/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.probestream;

import io.camunda.zeebe.engine.processing.streamprocessor.ProbeStreamer;
import io.camunda.zeebe.protocol.impl.stream.probe.ActivatedProbe;
import io.camunda.zeebe.protocol.impl.stream.probe.ProbeActivationProperties;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.transport.stream.api.RemoteStreamService;
import java.util.stream.Stream;

/** The probe streams of one physical tenant, as {@code JobStreamService} for job streams. */
public record ProbeStreamService(
    RemoteStreamService<ProbeActivationProperties, ActivatedProbe> remoteStreamService,
    ProbeStreamer probeStreamer,
    ProbeStreamErrorHandlerService errorHandlerService) {

  public ActorFuture<?> closeAsync(final ConcurrencyControl executor) {
    return Stream.of(remoteStreamService.closeAsync(executor), errorHandlerService.closeAsync())
        .collect(new ActorFutureCollector<>(executor));
  }
}
