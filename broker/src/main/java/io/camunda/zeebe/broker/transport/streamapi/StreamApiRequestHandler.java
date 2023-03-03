/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.streamapi;

import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.jobstream.StreamRegistry;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.stream.api.GatewayStreamer.Metadata;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

// Instantiate in broker with concrete types. We already know that it is job stream.
public final class StreamApiRequestHandler<M extends Metadata, P extends BufferWriter> extends Actor
    implements ClusterMembershipEventListener {
  private static final String TOPIC_ADD_STREAM = "job-stream-api-add";

  private final ClusterCommunicationService communicationService;
  private final ClusterMembershipService membershipService;
  private final StreamRegistry<M, P> streamRegistry;

  private final MetadataReader<M> metadataReader;

  public StreamApiRequestHandler(
      final ClusterCommunicationService communicationService,
      final ClusterMembershipService membershipService,
      final StreamRegistry streamRegistry,
      final MetadataReader<M> metadataReader) {
    this.communicationService = communicationService;
    this.membershipService = membershipService;
    this.streamRegistry = streamRegistry;
    this.metadataReader = metadataReader;
  }

  @Override
  protected void onActorStarting() {
    communicationService.subscribe(
        TOPIC_ADD_STREAM, Function.identity(), this::handleAddStreamRequest, actor::run);
    membershipService.addListener(this);
  }

  @Override
  protected void onActorClosing() {
    communicationService.unsubscribe(TOPIC_ADD_STREAM);
    membershipService.removeListener(this);
  }

  @Override
  public void event(final ClusterMembershipEvent event) {}

  private void handleAddStreamRequest(final MemberId sender, final byte[] rawRequest) {
    final var request = decodeRequest(rawRequest);

    // TODO: validate stream type (that there is one)
    // request.streamType().capacity() > 0
    final var metadata = metadataReader.read(request.metadata());
    if (metadata.isLeft()) {
      throw metadata.getLeft();
    }
    streamRegistry.add(request.streamType(), request.id(), sender, metadata.get());
  }

  private void handleRemoveStreamRequest(final MemberId sender, final byte[] rawRequest) {
    final var request = decodeRequest(rawRequest);

    streamRegistry.remove(request.id(), sender);
  }

  private ApiRequestReader decodeRequest(final byte[] bytes) {
    final var buffer = new UnsafeBuffer(bytes);
    final var reader = new ApiRequestReader();
    reader.wrap(buffer);

    return reader;
  }

  interface MetadataReader<M> {
    Either<RuntimeException, M> read(DirectBuffer buffer);
  }
}
