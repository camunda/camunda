/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.camunda.optimize.service.util.importing.EngineConstants.RUNNING_USER_TASK_INSTANCE_ENDPOINT;

public class RunningUserTaskInstanceEndpointImportIT extends AbstractImportEndpointFailureIT {

  @Override
  protected Stream<Arguments> getEndpointAndErrorResponses() {
    return Stream.of(
      RUNNING_USER_TASK_INSTANCE_ENDPOINT
    ).flatMap(endpoint -> engineErrors()
      .map(mockResp -> Arguments.of(endpoint, mockResp)));
  }

}
