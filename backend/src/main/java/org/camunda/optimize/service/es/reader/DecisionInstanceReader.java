/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;

@AllArgsConstructor
@Component
public class DecisionInstanceReader {
  private final DefinitionInstanceReader definitionInstanceReader;

  public Set<String> getExistingDecisionDefinitionKeysFromInstances() {
    return definitionInstanceReader.getAllExistingDefinitionKeys(DECISION);
  }

}
