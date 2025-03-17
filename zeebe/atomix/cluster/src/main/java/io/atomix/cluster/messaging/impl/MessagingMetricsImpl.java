/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import static io.atomix.cluster.messaging.impl.MessagingMetricsDoc.*;

import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.collection.Map3D;
import io.camunda.zeebe.util.collection.Table;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.jcip.annotations.ThreadSafe;

@ThreadSafe
final class MessagingMetricsImpl implements MessagingMetrics {

  private final Map<String, Timer> requestResponseLatency;
  private final MeterRegistry registry;
  private final Table<String, String, DistributionSummary> requestSize;

  /**
   * This Table and {@link this#requestRespCounter} refer to the same metric, but one is for
   * "MESSAGE" and the other for "REQ_RESP".
   *
   * <p>Using two maps avoid another level of nesting
   */
  private final Table<String, String, Counter> requestMessageCounter;

  private final Table<String, String, Counter> requestRespCounter;
  private final Map3D<String, String, String, Counter> responseCounter;
  private final Table<String, String, Counter> inFlightCounter;

  MessagingMetricsImpl(final MeterRegistry registry) {
    this.registry = registry;
    requestResponseLatency = new ConcurrentHashMap<>();
    requestSize = Table.concurrent();
    requestMessageCounter = Table.concurrent();
    requestRespCounter = Table.concurrent();
    responseCounter = Map3D.concurrent();
    inFlightCounter = Table.concurrent();
  }

  @Override
  public CloseableSilently startRequestTimer(final String name) {
    final var timer = getReqRespLatency(name);
    return MicrometerUtil.timer(timer, Timer.start(registry.config().clock()));
  }

  @Override
  public void observeRequestSize(final String to, final String name, final int requestSizeInBytes) {
    getRequestSizeSummary(to, name).record(requestSizeInBytes / 1_000f);
  }

  @Override
  public void countMessage(final String to, final String name) {
    requestMessageCounter
        .computeIfAbsent(to, name, (t, n) -> registerRequestCounter(MessageType.MESSAGE, t, n))
        .increment();
  }

  @Override
  public void countRequestResponse(final String to, final String name) {
    requestRespCounter
        .computeIfAbsent(to, name, (t, n) -> registerRequestCounter(MessageType.REQ_RESP, t, n))
        .increment();
  }

  @Override
  public void countSuccessResponse(final String address, final String name) {
    responseCounter
        .computeIfAbsent(address, name, "SUCCESS", this::registerResponseCounter)
        .increment();
  }

  @Override
  public void countFailureResponse(final String address, final String name, final String error) {
    responseCounter
        .computeIfAbsent(address, name, error, this::registerResponseCounter)
        .increment();
  }

  @Override
  public void incInFlightRequests(final String address, final String topic) {
    inFlightCounter.computeIfAbsent(address, topic, this::registerInFlightCounter).increment();
  }

  @Override
  public void decInFlightRequests(final String address, final String topic) {
    inFlightCounter.computeIfAbsent(address, topic, this::registerInFlightCounter).increment(-1.0D);
  }

  private DistributionSummary getRequestSizeSummary(final String to, final String name) {
    return requestSize.computeIfAbsent(
        to,
        name,
        (t, n) ->
            DistributionSummary.builder(REQUEST_SIZE_IN_KB.getName())
                .description(REQUEST_SIZE_IN_KB.getDescription())
                .serviceLevelObjectives(REQUEST_SIZE_IN_KB.getDistributionSLOs())
                .tags(
                    MessagingKeyNames.ADDRESS.asString(),
                    to,
                    MessagingKeyNames.TOPIC.asString(),
                    name)
                .register(registry));
  }

  private Timer getReqRespLatency(final String topic) {
    return requestResponseLatency.computeIfAbsent(
        topic,
        t ->
            Timer.builder(REQUEST_RESPONSE_LATENCY.getName())
                .description(REQUEST_RESPONSE_LATENCY.getDescription())
                .serviceLevelObjectives(REQUEST_RESPONSE_LATENCY.getTimerSLOs())
                .tag(MessagingKeyNames.TOPIC.asString(), t)
                .register(registry));
  }

  private Counter registerRequestCounter(
      final MessageType type, final String address, final String topic) {
    return Counter.builder(REQUEST_COUNT.getName())
        .description(REQUEST_COUNT.getDescription())
        .tags(
            MessagingKeyNames.TYPE.asString(),
            type.name(),
            MessagingKeyNames.ADDRESS.asString(),
            address,
            MessagingKeyNames.TOPIC.asString(),
            topic)
        .register(registry);
  }

  private Counter registerResponseCounter(
      final String address, final String topic, final String outcome) {
    return Counter.builder(RESPONSE_COUNT.getName())
        .description(RESPONSE_COUNT.getDescription())
        .tags(
            MessagingKeyNames.OUTCOME.asString(),
            outcome,
            MessagingKeyNames.ADDRESS.asString(),
            address,
            MessagingKeyNames.TOPIC.asString(),
            topic)
        .register(registry);
  }

  private Counter registerInFlightCounter(final String address, final String topic) {
    return Counter.builder(IN_FLIGHT_REQUESTS.getName())
        .description(IN_FLIGHT_REQUESTS.getDescription())
        .tags(
            MessagingKeyNames.ADDRESS.asString(),
            address,
            MessagingKeyNames.TOPIC.asString(),
            topic)
        .register(registry);
  }

  private enum MessageType {
    MESSAGE,
    REQ_RESP
  }
}
