/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.zeebe.containers.ZeebeNode;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;

public interface FlowControlActuator {
  static FlowControlActuator of(final ZeebeNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/flowControl", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  static FlowControlActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("flowControl").toString());
  }

  static FlowControlActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(FlowControlActuator.class, endpoint);
    final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    return Feign.builder()
        .encoder(new JacksonEncoder(mapper))
        .decoder(new JacksonDecoder(mapper))
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  public default Map<Integer, JsonNode> setFlowControlConfiguration(final String request) {
    final FlowControlCfg flowControlCfg;
    try {
      flowControlCfg = FlowControlCfg.deserialize(request);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return setFlowControlConfiguration(flowControlCfg);
  }

  /**
   * Sets the flow control configuration.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  Map<Integer, JsonNode> setFlowControlConfiguration(@RequestBody FlowControlCfg flowControlCfg);

  /**
   * Gets the flow control configuration.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("GET")
  @Headers({"Content-Type: application/json"})
  Map<Integer, JsonNode> getFlowControlConfiguration();
}
