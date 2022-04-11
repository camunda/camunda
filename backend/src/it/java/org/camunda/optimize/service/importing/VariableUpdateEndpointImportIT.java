/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_UPDATE_ENDPOINT;

public class VariableUpdateEndpointImportIT extends AbstractImportEndpointFailureIT {

  @Override
  protected Stream<Arguments> getEndpointAndErrorResponses() {
    return Stream.of(
      VARIABLE_UPDATE_ENDPOINT
    ).flatMap(endpoint -> engineErrors()
      .map(mockResp -> Arguments.of(endpoint, mockResp)));
  }

}
