/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.broker.system.management.PartitionStatus.ClockStatus;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.zeebe.containers.ZeebeBrokerNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Java interface for the broker's partitions actuator (roughly mapping to the broker admin
 * service). To instantiate this interface, you can use {@link Feign}; see {@link #of(String)} as an
 * example.
 *
 * <p>You can use one of {@link #of(String)} or {@link #of(ZeebeBrokerNode)} to create a new client
 * to use for yourself.
 *
 * <p>Adding a new method is simple: simply define the input/output here as you normally would, and
 * make sure to add the correct JSON encoding headers (`Accept` for the response type,
 * `Content-Type` if there's a body to send). See {@link LoggersActuator} for a more complete
 * example.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface PartitionsActuator {

  /**
   * Returns a {@link PartitionsActuator} instance using the given node as upstream. This only
   * accepts {@link ZeebeBrokerNode} at the moment, as only the broker has this actuator. It can be
   * changed if we move these to the gateway.
   *
   * @param node the node to connect to
   * @return a new instance of {@link PartitionsActuator}
   */
  static PartitionsActuator of(final ZeebeBrokerNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/partitions", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  /**
   * Returns a {@link PartitionsActuator} instance using the given node as upstream. This only
   * accepts {@link TestStandaloneBroker} at the moment, as only the broker has this actuator. It
   * can be changed if we move these to the gateway.
   *
   * @param node the node to connect to
   * @return a new instance of {@link PartitionsActuator}
   */
  static PartitionsActuator of(final TestStandaloneBroker node) {
    return of(node.actuatorUri("partitions").toString());
  }

  /**
   * Returns a {@link PartitionsActuator} instance using the given endpoint as upstream. The
   * endpoint is expected to be a complete absolute URL, e.g.
   * "http://localhost:9600/actuator/partitions".
   *
   * @param endpoint the actuator URL to connect to
   * @return a new instance of {@link PartitionsActuator}
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  static PartitionsActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(PartitionsActuator.class, endpoint);
    final var decoder = new JacksonDecoder(List.of(new Jdk8Module(), new JavaTimeModule()));
    return Feign.builder()
        .encoder(new JacksonEncoder())
        .decoder(decoder)
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  @RequestLine("GET")
  @Headers("Accept: application/json")
  Map<Integer, PartitionStatus> query();

  @RequestLine("POST /pauseExporting")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  Map<Integer, PartitionStatus> pauseExporting();

  @RequestLine("POST /softPauseExporting")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  Map<Integer, PartitionStatus> softPauseExporting();

  @RequestLine("POST /resumeExporting")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  Map<Integer, PartitionStatus> resumeExporting();

  @RequestLine("POST /pauseProcessing")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  Map<Integer, PartitionStatus> pauseProcessing();

  @RequestLine("POST /resumeProcessing")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  Map<Integer, PartitionStatus> resumeProcessing();

  @RequestLine("POST /takeSnapshot")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  Map<Integer, PartitionStatus> takeSnapshot();

  @RequestLine("POST /prepareUpgrade")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  Map<Integer, PartitionStatus> prepareUpgrade();

  @JsonIgnoreProperties(ignoreUnknown = true)
  record PartitionStatus(
      String role,
      String snapshotId,
      Long processedPosition,
      Long processedPositionInSnapshot,
      String streamProcessorPhase,
      Long exportedPosition,
      String exporterPhase,
      ClockStatus clock) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ClockStatus(Instant instant, String modificationType, Map<String, Object> modification) {}
}
