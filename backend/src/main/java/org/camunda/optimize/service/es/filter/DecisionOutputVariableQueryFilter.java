/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class DecisionOutputVariableQueryFilter extends DecisionVariableQueryFilter {

  public DecisionOutputVariableQueryFilter(final DateTimeFormatter formatter) {
    super(formatter);
  }

  @Override
  String getVariablePath() {
    return DecisionInstanceType.OUTPUTS;
  }

}
