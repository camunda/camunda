/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.variable;

import org.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExternalVariableService {

  public void storeExternalProcessVariables(final List<ExternalProcessVariableDto> externalProcessVariables) {
    // TODO with OPT-5500
  }
}
