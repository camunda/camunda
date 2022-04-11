/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.rest.AbstractVariableLabelIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class PublicApiProcessVariableLabelIT extends AbstractVariableLabelIT {

  private final String ACCESS_TOKEN = "aToken";

  @BeforeEach
  public void setup() {
    embeddedOptimizeExtension.getConfigurationService()
      .getOptimizeApiConfiguration()
      .setAccessToken(ACCESS_TOKEN);
  }

  @Test
  public void updateVariableLabelsWithoutAccessToken() {
    // given
    DefinitionVariableLabelsDto definitionVariableLabelsDto = new DefinitionVariableLabelsDto(
      PROCESS_DEFINITION_KEY,
      Collections.emptyList()
    );

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableLabelRequest(definitionVariableLabelsDto, null)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Override
  protected Response executeUpdateProcessVariableLabelRequest(final DefinitionVariableLabelsDto labelOptimizeDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildProcessVariableLabelRequest(labelOptimizeDto, ACCESS_TOKEN)
      .execute();
  }

}
