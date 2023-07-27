/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.shared.management.openapi.models.jobstreams.ClientJobStream;
import io.camunda.zeebe.shared.management.openapi.models.jobstreams.Error;
import io.camunda.zeebe.shared.management.openapi.models.jobstreams.JobStreams;
import io.camunda.zeebe.shared.management.openapi.models.jobstreams.Metadata;
import io.camunda.zeebe.shared.management.openapi.models.jobstreams.RemoteJobStream;
import io.camunda.zeebe.shared.management.openapi.models.jobstreams.RemoteStreamId;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamInfo;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

/**
 * An actuator endpoint which allows viewing the current registered streams. Here {@code remote}
 * refers to {@link io.camunda.zeebe.transport.stream.api.RemoteStream} instances registered on the
 * broker, and {@code client} refers to all {@link ClientStream} instances registered on the
 * gateway.
 *
 * <p>If running a broker with an embedded gateway, both can be viewed; otherwise, one or the other
 * will always be empty.
 */
@Component
@WebEndpoint(id = "jobstreams")
public final class JobStreamEndpoint {
  private static final Set<String> TYPES = Set.of("remote", "client");

  private final JobStreamEndpointService service;
  private final AtomixCluster cluster;

  @Autowired
  public JobStreamEndpoint(final JobStreamEndpointService service, final AtomixCluster cluster) {
    this.service = service;
    this.cluster = cluster;
  }

  @ReadOperation
  public WebEndpointResponse<JobStreams> list() {
    return new WebEndpointResponse<>(
        new JobStreams().client(getClientStreams()).remote(getRemoteStreams()),
        200,
        MimeTypeUtils.APPLICATION_JSON);
  }

  @ReadOperation
  public WebEndpointResponse<?> list(final @Selector String type) {
    if (!TYPES.contains(type)) {
      return new WebEndpointResponse<>(
          new Error()
              .message("No known stream type '%s'; should be one of %s".formatted(type, TYPES)),
          400,
          MimeTypeUtils.APPLICATION_JSON);
    }

    final Collection<?> streams = "client".equals(type) ? getClientStreams() : getRemoteStreams();
    return new WebEndpointResponse<>(streams, 200, MimeTypeUtils.APPLICATION_JSON);
  }

  private List<RemoteJobStream> getRemoteStreams() {
    return transformRemote(service.remoteJobStreams());
  }

  private List<ClientJobStream> getClientStreams() {
    return transformClient(service.clientJobStreams());
  }

  private List<RemoteJobStream> transformRemote(
      final Collection<RemoteStreamInfo<JobActivationProperties>> streams) {
    return streams.stream().map(this::transformRemote).toList();
  }

  private List<ClientJobStream> transformClient(
      final Collection<ClientStream<JobActivationProperties>> streams) {
    return streams.stream().map(this::transformClient).toList();
  }

  private RemoteJobStream transformRemote(final RemoteStreamInfo<JobActivationProperties> stream) {
    final var consumers =
        stream.consumers().stream()
            .map(id -> new RemoteStreamId().id(id.streamId()).receiver(id.receiver().id()))
            .toList();
    return new RemoteJobStream()
        .jobType(BufferUtil.bufferAsString(stream.streamType()))
        .metadata(transform(stream.metadata()))
        .consumers(consumers);
  }

  private ClientJobStream transformClient(final ClientStream<JobActivationProperties> stream) {
    final var brokers =
        cluster.getMembershipService().getMembers().stream()
            .map(Member::id)
            .filter(stream::isConnected)
            .map(MemberId::id)
            .map(Integer::valueOf)
            .toList();

    return new ClientJobStream()
        .jobType(BufferUtil.bufferAsString(stream.streamType()))
        .metadata(transform(stream.metadata()))
        .id(stream.streamId())
        .connectedTo(brokers);
  }

  private Metadata transform(final JobActivationProperties properties) {
    return new Metadata()
        .worker(BufferUtil.bufferAsString(properties.worker()))
        .timeout(properties.timeout())
        .fetchVariables(
            properties.fetchVariables().stream().map(BufferUtil::bufferAsString).toList());
  }
}
