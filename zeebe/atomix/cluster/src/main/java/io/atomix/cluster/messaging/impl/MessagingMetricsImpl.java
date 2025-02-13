/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.cluster.messaging.impl;

import io.camunda.zeebe.util.CloseableSilently;
<<<<<<< HEAD
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
=======
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
>>>>>>> 444c8eee (fix: make MessagingMetricsImpl ThreadSafe)

@ThreadSafe
final class MessagingMetricsImpl implements MessagingMetrics {

  private static final String NAMESPACE = "zeebe";
  private static final String LABEL_TOPIC = "topic";
  private static final String LABEL_ADDRESS = "address";
  private static final String REQ_TYPE_MESSAGE = "MESSAGE";
  private static final String REQ_TYPE_REQ_RESP = "REQ_RESP";

  private static final Histogram REQUEST_RESPONSE_LATENCY =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("messaging_request_response_latency")
          .help("The time how long it takes to retrieve a response for a request")
          .labelNames(LABEL_TOPIC)
          .register();

  private static final Histogram REQUEST_SIZE_IN_KB =
      Histogram.build()
          .namespace(NAMESPACE)
          .name("messaging_request_size_kb")
          .help("The size of the request, which has been sent")
          .labelNames(LABEL_ADDRESS, LABEL_TOPIC)
          .buckets(.01, .1, .250, 1, 10, 100, 500, 1_000, 2_000, 4_000)
          .register();
  private static final Counter REQUEST_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("messaging_request_count")
          .help("Number of requests which has been send to a certain address")
          .labelNames("type", LABEL_ADDRESS, LABEL_TOPIC)
          .register();

<<<<<<< HEAD
  private static final Counter RESPONSE_COUNT =
      Counter.build()
          .namespace(NAMESPACE)
          .name("messaging_response_count")
          .help("Number of responses which has been received")
          .labelNames(LABEL_ADDRESS, LABEL_TOPIC, "outcome")
          .register();

  private static final Gauge IN_FLIGHT_REQUESTS =
      Gauge.build()
          .namespace(NAMESPACE)
          .name("messaging_inflight_requests")
          .help("The count of inflight requests")
          .labelNames(LABEL_ADDRESS, LABEL_TOPIC)
          .register();
=======
  MessagingMetricsImpl(final MeterRegistry registry) {
    this.registry = registry;
    requestResponseLatency = new ConcurrentHashMap<>();
    requestSize = Table.concurrent();
    requestMessageCounter = Table.concurrent();
    requestRespCounter = Table.concurrent();
    responseCounter = Map3D.concurrent();
    inFlightCounter = Table.concurrent();
  }
>>>>>>> 444c8eee (fix: make MessagingMetricsImpl ThreadSafe)

  @Override
  public CloseableSilently startRequestTimer(final String name) {
    final var timer = REQUEST_RESPONSE_LATENCY.labels(name).startTimer();
    return timer::close;
  }

  @Override
  public void observeRequestSize(final String to, final String name, final int requestSizeInBytes) {
    REQUEST_SIZE_IN_KB.labels(to, name).observe(requestSizeInBytes / 1_000f);
  }

  @Override
  public void countMessage(final String to, final String name) {
    REQUEST_COUNT.labels(REQ_TYPE_MESSAGE, to, name).inc();
  }

  @Override
  public void countRequestResponse(final String to, final String name) {
    REQUEST_COUNT.labels(REQ_TYPE_REQ_RESP, to, name).inc();
  }

  @Override
  public void countSuccessResponse(final String address, final String name) {
    RESPONSE_COUNT.labels(address, name, "SUCCESS").inc();
  }

  @Override
  public void countFailureResponse(final String address, final String name, String error) {
    RESPONSE_COUNT.labels(address, name, error).inc();
  }

  @Override
  public void incInFlightRequests(final String address, String topic) {
    IN_FLIGHT_REQUESTS.labels(address, topic).inc();
  }

  @Override
  public void decInFlightRequests(final String address, String topic) {
    IN_FLIGHT_REQUESTS.labels(address, topic).dec();
  }
}
