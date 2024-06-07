/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_UPDATE_ENDPOINT;

import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.provider.Arguments;

@Tag(OPENSEARCH_PASSING)
public class VariableUpdateEndpointImportIT extends AbstractImportEndpointFailureIT {

  @Override
  protected Stream<Arguments> getEndpointAndErrorResponses() {
    return Stream.of(VARIABLE_UPDATE_ENDPOINT)
        .flatMap(endpoint -> engineErrors().map(mockResp -> Arguments.of(endpoint, mockResp)));
  }
}
