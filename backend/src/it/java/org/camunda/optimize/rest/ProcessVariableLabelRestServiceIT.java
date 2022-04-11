/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessVariableLabelRestServiceIT extends AbstractVariableLabelIT {

  @Test
  public void updateVariableLabelForUnauthenticatedUser() {
    // given
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      Collections.emptyList()
    );

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableLabelRequest(definitionVariableLabelsDto)
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Override
  protected Response executeUpdateProcessVariableLabelRequest(final DefinitionVariableLabelsDto labelOptimizeDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableLabelRequest(labelOptimizeDto)
      .execute();
  }

}
