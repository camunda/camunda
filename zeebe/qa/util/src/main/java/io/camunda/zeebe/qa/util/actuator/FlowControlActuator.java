/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.actuator;

import java.util.List;

public class FlowControlActuator {
  private final GetFlowControlActuator getClient;
  private final SetFlowControlActuator postClient;

  public FlowControlActuator(
      final GetFlowControlActuator getClient, final SetFlowControlActuator postClient) {
    this.getClient = getClient;
    this.postClient = postClient;
  }

  public List<String> getFlowControlConfiguration() {
    return getClient.getFlowControlConfiguration();
  }

  public String setFlowControlConfiguration(final String request, final String append) {
    return postClient.setFlowControlConfiguration(request, append);
  }
}
