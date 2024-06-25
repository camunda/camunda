/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.jackson.JacksonEncoder;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.zeebe.containers.ZeebeNode;
import org.springframework.web.bind.annotation.RequestBody;

public interface SetFlowControlActuator {
  static SetFlowControlActuator of(final ZeebeNode<?> node) {
    final var endpoint =
        String.format("http://%s/actuator/flowControl", node.getExternalMonitoringAddress());
    return of(endpoint);
  }

  static SetFlowControlActuator of(final TestApplication<?> node) {
    return of(node.actuatorUri("flowControl").toString());
  }

  static SetFlowControlActuator of(final String endpoint) {
    final var target = new HardCodedTarget<>(SetFlowControlActuator.class, endpoint);
    final ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    return Feign.builder()
        .encoder(new JacksonEncoder(mapper))
        .retryer(Retryer.NEVER_RETRY)
        .target(target);
  }

  /**
   * Sets the flow control configuration.
   *
   * @throws feign.FeignException if the request is not successful (e.g. 4xx or 5xx)
   */
  @RequestLine("POST")
  @Headers({"Content-Type: application/json", "Accept: application/json"})
  String setFlowControlConfiguration(@RequestBody FlowControlCfg flowControlCfg);
}
