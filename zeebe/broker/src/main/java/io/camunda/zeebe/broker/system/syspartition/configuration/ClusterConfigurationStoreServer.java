/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.configuration;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueReader;
import io.camunda.zeebe.broker.system.syspartition.SystemKeyValueWriter;
import io.camunda.zeebe.broker.system.syspartition.SystemPartition;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionKeyType;
import io.camunda.zeebe.broker.system.syspartition.SystemPartitionListener;
import io.camunda.zeebe.broker.system.syspartition.configuration.ClusterConfigurationStoreProtocol.CasRequest;
import io.camunda.zeebe.broker.system.syspartition.configuration.ClusterConfigurationStoreProtocol.Response;
import io.camunda.zeebe.broker.system.syspartition.configuration.ClusterConfigurationStoreProtocol.Status;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationStore;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.scheduler.Actor;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Serves reads and compare-and-sets of the cluster configuration stored in the system partition, on
 * every system partition member; only the leader answers, the others reply {@link
 * Status#NOT_LEADER}.
 *
 * <p>Compare-and-sets run one at a time, each comparing against the store only once the previous
 * one is applied, so two coordinators expecting the same next change id cannot both succeed.
 */
public final class ClusterConfigurationStoreServer extends Actor
    implements SystemPartitionListener {
  private static final SystemPartitionKeyType KEY = SystemPartitionKeyType.CLUSTER_CONFIGURATION;

  private final ClusterCommunicationService communicationService;
  private final SystemPartition systemPartition;
  private final SystemKeyValueReader reader;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();
  private final Queue<PendingCas> pendingCas = new ArrayDeque<>();
  private SystemKeyValueWriter writer;
  private boolean casInFlight;

  public ClusterConfigurationStoreServer(
      final ClusterCommunicationService communicationService,
      final SystemPartition systemPartition) {
    super("ClusterConfigurationStoreServer", SystemPartition.PARTITION_ID);
    this.communicationService = communicationService;
    this.systemPartition = systemPartition;
    reader = systemPartition.keyValueReader();
  }

  @Override
  protected void onActorStarted() {
    communicationService.replyToAsync(
        ClusterConfigurationStoreProtocol.READ_SUBJECT,
        Function.identity(),
        this::onRead,
        Response::encode,
        actor::run);
    communicationService.replyToAsync(
        ClusterConfigurationStoreProtocol.CAS_SUBJECT,
        CasRequest::decode,
        this::onCas,
        Response::encode,
        actor::run);
  }

  @Override
  protected void onActorClosing() {
    communicationService.unsubscribe(ClusterConfigurationStoreProtocol.READ_SUBJECT);
    communicationService.unsubscribe(ClusterConfigurationStoreProtocol.CAS_SUBJECT);
    writer = null;
    pendingCas.forEach(cas -> cas.result.complete(notLeader()));
    pendingCas.clear();
  }

  @Override
  public void onBecomingLeader(final long term, final SystemKeyValueWriter writer) {
    actor.run(() -> this.writer = writer);
  }

  @Override
  public void onBecomingFollower(final long term) {
    actor.run(() -> writer = null);
  }

  @Override
  public void onBecomingInactive(final long term) {
    actor.run(() -> writer = null);
  }

  private CompletableFuture<Response> onRead(final byte[] ignored) {
    if (writer == null) {
      return CompletableFuture.completedFuture(notLeader());
    }
    return CompletableFuture.completedFuture(
        Response.ok(reader.get(KEY, new BytesValue()).map(BytesValue::bytes).orElse(new byte[0])));
  }

  private CompletableFuture<Response> onCas(final CasRequest request) {
    final var result = new CompletableFuture<Response>();
    pendingCas.add(new PendingCas(request, result));
    processNextCas();
    return result;
  }

  private void processNextCas() {
    if (casInFlight || pendingCas.isEmpty()) {
      return;
    }
    final var cas = pendingCas.poll();
    final var currentWriter = writer;
    if (currentWriter == null) {
      cas.result.complete(notLeader());
      processNextCas();
      return;
    }

    final long storedNextId;
    try {
      storedNextId = storedNextId();
    } catch (final RuntimeException e) {
      cas.result.complete(Response.error(Status.FAILED, e.toString()));
      processNextCas();
      return;
    }
    if (storedNextId != cas.request.expectedNextId()) {
      cas.result.complete(
          Response.error(
              Status.REJECTED,
              "Expected the stored configuration's next change id to be %d, but it is %d"
                  .formatted(cas.request.expectedNextId(), storedNextId)));
      processNextCas();
      return;
    }

    casInFlight = true;
    actor.runOnCompletion(
        currentWriter.put(KEY, new BytesValue(cas.request.configuration())),
        (position, putError) -> {
          if (putError != null) {
            completeCas(cas, Response.error(Status.FAILED, putError.toString()));
            return;
          }
          // the next compare-and-set must compare against this one
          actor.runOnCompletion(
              systemPartition.awaitApplied(position),
              (ok, applyError) ->
                  completeCas(
                      cas,
                      applyError == null
                          ? Response.ok(new byte[0])
                          : Response.error(Status.FAILED, applyError.toString())));
        });
  }

  private void completeCas(final PendingCas cas, final Response response) {
    casInFlight = false;
    cas.result.complete(response);
    processNextCas();
  }

  private long storedNextId() {
    return reader
        .get(KEY, new BytesValue())
        .map(
            value ->
                serializer
                    .decodeCurrentClusterConfiguration(value.bytes())
                    .phasedChangeState()
                    .nextId())
        .orElse(ClusterConfigurationStore.ABSENT);
  }

  private static Response notLeader() {
    return Response.error(Status.NOT_LEADER, "Not the system partition leader");
  }

  private record PendingCas(CasRequest request, CompletableFuture<Response> result) {}
}
