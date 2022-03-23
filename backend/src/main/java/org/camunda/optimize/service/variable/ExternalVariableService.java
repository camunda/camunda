/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.variable;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.camunda.optimize.service.es.writer.variable.ExternalProcessVariableWriter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExternalVariableService {

  private final ExternalProcessVariableWriter externalProcessVariableWriter;

  public void storeExternalProcessVariables(final List<ExternalProcessVariableDto> externalProcessVariables) {
    externalProcessVariableWriter.writeExternalProcessVariables(externalProcessVariables);
  }
}
