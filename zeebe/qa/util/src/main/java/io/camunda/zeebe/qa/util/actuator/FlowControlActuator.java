/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;

public class FlowControlActuator {
  private final GetFlowControlActuator getClient;
  private final SetFlowControlActuator postClient;

  public FlowControlActuator(
      final GetFlowControlActuator getClient, final SetFlowControlActuator postClient) {
    this.getClient = getClient;
    this.postClient = postClient;
  }

  public String getFlowControlConfiguration() {
    return getClient.getFlowControlConfiguration();
  }

  public String setFlowControlConfiguration(final String request) {
    final FlowControlCfg flowControlCfg;
    try {
      flowControlCfg = FlowControlCfg.fromJson(request);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return postClient.setFlowControlConfiguration(flowControlCfg);
  }
}
