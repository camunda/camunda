/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.db.reader.DecisionInstanceReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@Component
@Conditional(OpenSearchCondition.class)
public class DecisionInstanceReaderOS implements DecisionInstanceReader {

  @Override
  public Set<String> getExistingDecisionDefinitionKeysFromInstances() {
    //todo will be handled in the OPT-7230
    return new HashSet<>();
  }

}
