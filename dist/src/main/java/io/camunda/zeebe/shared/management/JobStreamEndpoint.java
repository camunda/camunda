/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.transport.stream.api.RemoteStreamInfo;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

  private final Service service;

  @Autowired
  public JobStreamEndpoint(final Service service) {
    this.service = Objects.requireNonNull(service, "must specify a job stream service");
  }

  /**
   * Returns the complete list of remote and client job streams as an object with two keys: {@code
   * remote} and {@code client}. Both are collections of the job streams of the appropriate type.
   *
   * <p>This view is mostly used for human debugging to quickly correlate the state of a stream on
   * the gateway and brokers.
   */
  @ReadOperation
  public WebEndpointResponse<JobStreams> list() {
    return new WebEndpointResponse<>(
        new JobStreams(getRemoteStreams(), getClientStreams()),
        200,
        MimeTypeUtils.APPLICATION_JSON);
  }

  /**
   * Returns either the list of {@code client} or {@code remote} job streams, based on the given
   * {@code type}. If the type is unknown, returns a 400 with a singleton map containing an error
   * field with an appropriate message.
   *
   * @param type the type of streams to return
   */
  @ReadOperation
  public WebEndpointResponse<?> list(final @Selector String type) {
    if (!TYPES.contains(type)) {
      return new WebEndpointResponse<>(
          Map.of("error", "No known stream type '%s'; should be one of %s".formatted(type, TYPES)),
          400,
          MimeTypeUtils.APPLICATION_JSON);
    }

    final Collection<?> streams = "client".equals(type) ? getClientStreams() : getRemoteStreams();
    return new WebEndpointResponse<>(streams, 200, MimeTypeUtils.APPLICATION_JSON);
  }

  private Collection<RemoteJobStream> getRemoteStreams() {
    return transformRemote(service.remoteJobStreams());
  }

  private Collection<ClientJobStream> getClientStreams() {
    return transformClient(service.clientJobStreams());
  }

  private Collection<RemoteJobStream> transformRemote(
      final Collection<RemoteStreamInfo<JobActivationProperties>> streams) {
    return streams.stream().map(this::transformRemote).toList();
  }

  private Collection<ClientJobStream> transformClient(
      final Collection<ClientStream<JobActivationProperties>> streams) {
    return streams.stream().map(this::transformClient).toList();
  }

  private RemoteJobStream transformRemote(final RemoteStreamInfo<JobActivationProperties> stream) {
    final var consumers =
        stream.consumers().stream()
            .map(id -> new RemoteStreamId(id.streamId(), id.receiver().id()))
            .toList();
    return new RemoteJobStream(
        BufferUtil.bufferAsString(stream.streamType()), transform(stream.metadata()), consumers);
  }

  private ClientJobStream transformClient(final ClientStream<JobActivationProperties> stream) {
    // it's safe to cast any filtered member ID to an integer, since a client stream can only be
    // connected to a broker, and brokers always have integer node IDs
    final var brokers =
        stream.liveConnections().stream().map(MemberId::id).map(Integer::valueOf).toList();

    return new ClientJobStream(
        BufferUtil.bufferAsString(stream.streamType()),
        stream.streamId(),
        transform(stream.metadata()),
        brokers);
  }

  private Metadata transform(final JobActivationProperties properties) {
    return new Metadata(
        BufferUtil.bufferAsString(properties.worker()),
        Duration.ofMillis(properties.timeout()),
        properties.fetchVariables().stream().map(BufferUtil::bufferAsString).toList(),
        properties.tenantIds(),
        properties.tenantFilter());
  }

  /** View model for the combined list of all remote and client job streams. */
  public record JobStreams(
      Collection<RemoteJobStream> remote, Collection<ClientJobStream> client) {}

  /** View model of a single remote job stream for JSON serialization */
  public record RemoteJobStream(
      String jobType, Metadata metadata, Collection<RemoteStreamId> consumers)
      implements JobStream {}

  /**
   * View model of a client job stream for JSON serialization. The {@link #connectedTo()} collection
   * is the set of broker IDs this stream is registered on, from the gateway's point of view.
   */
  public record ClientJobStream(
      String jobType, Object id, Metadata metadata, Collection<Integer> connectedTo)
      implements JobStream {}

  /** View model for the {@link JobActivationProperties} of a job stream. */
  public record Metadata(
      String worker,
      Duration timeout,
      Collection<String> fetchVariables,
      Collection<String> tenantIds,
      TenantFilter tenantFilter) {}

  /** View model for a remote job stream ID */
  public record RemoteStreamId(UUID id, String receiver) {}

  public interface Service {

    /** Returns the list of registered remote/broker job streams. */
    Collection<RemoteStreamInfo<JobActivationProperties>> remoteJobStreams();

    /** Returns the list of registered client/gateway job streams. */
    Collection<ClientStream<JobActivationProperties>> clientJobStreams();
  }

  public interface JobStream {
    Metadata metadata();

    String jobType();
  }
}
