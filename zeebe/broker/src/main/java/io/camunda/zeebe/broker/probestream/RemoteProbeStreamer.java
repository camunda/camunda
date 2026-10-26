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
import io.camunda.zeebe.transport.stream.api.RemoteStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamer;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.DirectBuffer;

/** Finds probe streams among the streams gateways registered with this broker. */
public final class RemoteProbeStreamer implements ProbeStreamer {

  private final RemoteStreamer<ProbeActivationProperties, ActivatedProbe> delegate;

  public RemoteProbeStreamer(
      final RemoteStreamer<ProbeActivationProperties, ActivatedProbe> delegate) {
    this.delegate = delegate;
  }

  @Override
  public Optional<ProbeStream> streamFor(
      final DirectBuffer streamType, final Predicate<ProbeActivationProperties> filter) {
    return delegate.streamFor(streamType, filter).map(RemoteProbeStream::new);
  }

  private record RemoteProbeStream(
      RemoteStream<ProbeActivationProperties, ActivatedProbe> remoteStream) implements ProbeStream {

    @Override
    public ProbeActivationProperties properties() {
      return remoteStream.metadata();
    }

    @Override
    public void push(final ActivatedProbe payload) {
      remoteStream.push(payload);
    }
  }
}
