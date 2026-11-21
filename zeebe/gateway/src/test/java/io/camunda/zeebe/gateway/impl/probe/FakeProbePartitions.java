/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probe;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerResponseConsumer;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerErrorResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedTopologyManager;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeYieldRequest;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeBatchIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A broker client in front of fake partitions that keep activatable probes per physical tenant and
 * partition, as the engine does. Activation collects them, {@code YIELD} puts them back. A test can
 * hold a partition's response open, or make a partition answer with an error.
 */
final class FakeProbePartitions implements BrokerClient {

  static final int PARTITIONS = 3;

  private final BrokerTopologyManager topologyManager = new StubbedTopologyManager(PARTITIONS);
  private final Map<String, List<Consumer<String>>> subscribers = new HashMap<>();
  private final Map<String, Map<Integer, List<Probe>>> activatable = new HashMap<>();
  private final Map<Integer, Integer> nextKeyByPartition = new HashMap<>();
  private final Map<Long, String> probeIdByKey = new HashMap<>();
  private final Map<Integer, ErrorCode> errorByPartition = new HashMap<>();
  private final List<Activation> activations = new ArrayList<>();
  private final List<BrokerProbeYieldRequest> yields = new ArrayList<>();
  private int jobActivations;
  private Integer heldPartition;
  private CompletableFuture<BrokerResponse<ProbeBatchRecord>> heldResponse;
  private Runnable onNextActivation = () -> {};
  private boolean rejectActivations;
  private int truncateAfter = Integer.MAX_VALUE;

  /** Makes a probe activatable on the partition and returns its key. */
  long addProbe(
      final String physicalTenantId,
      final int partitionId,
      final String probeId,
      final int inputSize) {
    final int index = nextKeyByPartition.merge(partitionId, 1, Integer::sum);
    final long key = Protocol.encodePartitionId(partitionId, index);
    probesOf(physicalTenantId, partitionId).add(new Probe(key, probeId, inputSize));
    return key;
  }

  long addProbe(final String physicalTenantId, final int partitionId, final String probeId) {
    return addProbe(physicalTenantId, partitionId, probeId, 0);
  }

  void notifyProbesAvailable(final String physicalTenantId, final String probeId) {
    notify(physicalTenantId + "-probesAvailable", probeId);
  }

  void notify(final String topic, final String payload) {
    subscribers.getOrDefault(topic, List.of()).forEach(handler -> handler.accept(payload));
  }

  /** The next activation on this partition gets no response until {@link #releaseResponse()}. */
  void holdNextResponseOf(final int partitionId) {
    heldPartition = partitionId;
  }

  void releaseResponse() {
    final var response = heldResponse;
    heldResponse = null;
    response.complete(new BrokerResponse<>(new ProbeBatchRecord(), heldPartition, 1));
    heldPartition = null;
  }

  void answerWithError(final int partitionId, final ErrorCode errorCode) {
    errorByPartition.put(partitionId, errorCode);
  }

  void rejectActivations() {
    rejectActivations = true;
  }

  /** Like the engine when the batch is full: at most this many probes, and the rest later. */
  void truncateAfter(final int probes) {
    truncateAfter = probes;
  }

  /** Runs once, right after the next activation is answered. */
  void onNextActivation(final Runnable action) {
    onNextActivation = action;
  }

  List<Activation> activations() {
    return activations;
  }

  List<BrokerProbeYieldRequest> yields() {
    return yields;
  }

  int jobActivations() {
    return jobActivations;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<BrokerResponse<T>> sendRequest(final BrokerRequest<T> request) {
    if (request instanceof final BrokerActivateProbesRequest activation) {
      return (CompletableFuture<BrokerResponse<T>>) (Object) activate(activation);
    }
    if (request instanceof BrokerActivateJobsRequest) {
      jobActivations++;
      return CompletableFuture.completedFuture(
          (BrokerResponse<T>) new BrokerResponse<>(new JobBatchRecord(), 1, 1));
    }
    throw new UnsupportedOperationException("Unexpected request " + request);
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequest(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    return sendRequest(request);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request) {
    if (request instanceof final BrokerProbeYieldRequest yield) {
      yields.add(yield);
      final int partitionId = Protocol.decodePartitionId(yield.getKey());
      probesOf(yield.getPartitionGroup(), partitionId)
          .add(new Probe(yield.getKey(), probeIdByKey.get(yield.getKey()), 0));
      return CompletableFuture.completedFuture(
          (BrokerResponse<T>) new BrokerResponse<>(new ProbeRecord(), partitionId, yield.getKey()));
    }
    throw new UnsupportedOperationException("Unexpected request " + request);
  }

  @Override
  public <T> CompletableFuture<BrokerResponse<T>> sendRequestWithRetry(
      final BrokerRequest<T> request, final Duration requestTimeout) {
    return sendRequestWithRetry(request);
  }

  @Override
  public <T> void sendRequestWithRetry(
      final BrokerRequest<T> request,
      final BrokerResponseConsumer<T> responseConsumer,
      final Consumer<Throwable> throwableConsumer) {
    throw new UnsupportedOperationException();
  }

  @Override
  public BrokerTopologyManager getTopologyManager() {
    return topologyManager;
  }

  @Override
  public void subscribeJobAvailableNotification(
      final String topic, final Object subscriber, final Consumer<String> handler) {
    // the real client dedups by (topic, subscriber); one subscriber per topic is enough here
    subscribers.computeIfAbsent(topic, t -> new ArrayList<>());
    if (subscribers.get(topic).isEmpty()) {
      subscribers.get(topic).add(handler);
    }
  }

  @Override
  public Collection<ActorFuture<Void>> start() {
    return List.of();
  }

  @Override
  public void close() {}

  private CompletableFuture<BrokerResponse<ProbeBatchRecord>> activate(
      final BrokerActivateProbesRequest request) {
    final int partitionId = request.getPartitionId();
    final var value = request.getRequestWriter();
    activations.add(
        new Activation(
            request.getPartitionGroup(),
            partitionId,
            value.getMaxProbesToActivate(),
            request.getAuthorization()));
    try {
      if (rejectActivations) {
        return CompletableFuture.completedFuture(
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    ProbeBatchIntent.ACTIVATE, -1, RejectionType.FORBIDDEN, "not assigned")));
      }
      final var errorCode = errorByPartition.get(partitionId);
      if (errorCode != null) {
        return CompletableFuture.completedFuture(
            new BrokerErrorResponse<>(new BrokerError(errorCode, "fake error")));
      }
      if (Integer.valueOf(partitionId).equals(heldPartition) && heldResponse == null) {
        heldResponse = new CompletableFuture<>();
        return heldResponse;
      }
      final var batch = new ProbeBatchRecord();
      final var probes = probesOf(request.getPartitionGroup(), partitionId);
      final Iterator<Probe> iterator = probes.iterator();
      int collected = 0;
      while (iterator.hasNext() && collected < value.getMaxProbesToActivate()) {
        final var probe = iterator.next();
        if (!value.getProbeIds().contains(probe.probeId())) {
          continue;
        }
        if (collected == truncateAfter) {
          batch.setTruncated(true);
          break;
        }
        iterator.remove();
        batch.addProbe(probe.key(), probe.toRecord());
        collected++;
      }
      return CompletableFuture.completedFuture(new BrokerResponse<>(batch, partitionId, 1));
    } finally {
      final var action = onNextActivation;
      onNextActivation = () -> {};
      action.run();
    }
  }

  private List<Probe> probesOf(final String physicalTenantId, final int partitionId) {
    return activatable
        .computeIfAbsent(physicalTenantId, t -> new HashMap<>())
        .computeIfAbsent(partitionId, p -> new ArrayList<>());
  }

  record Activation(
      String physicalTenantId, int partitionId, int maxProbesToActivate, AuthInfo authorization) {}

  private final class Probe {
    private final long key;
    private final String probeId;
    private final int inputSize;

    private Probe(final long key, final String probeId, final int inputSize) {
      this.key = key;
      this.probeId = probeId;
      this.inputSize = inputSize;
      probeIdByKey.put(key, probeId);
    }

    private long key() {
      return key;
    }

    private String probeId() {
      return probeId;
    }

    private ProbeRecord toRecord() {
      final var record = new ProbeRecord().setProbeId(probeId);
      if (inputSize > 0) {
        record.setInput(
            new UnsafeBuffer(
                MsgPackConverter.convertToMsgPack(Map.of("payload", "x".repeat(inputSize)))));
      }
      return record;
    }
  }
}
