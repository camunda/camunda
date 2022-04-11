/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.springframework.stereotype.Component;

@Component
public class DecisionOutputVariableQueryFilter extends DecisionVariableQueryFilter {

  @Override
  String getVariablePath() {
    return DecisionInstanceIndex.OUTPUTS;
  }

}
