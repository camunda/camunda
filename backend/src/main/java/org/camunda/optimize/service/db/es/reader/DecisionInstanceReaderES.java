/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.reader;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.db.reader.DecisionInstanceReader;
import org.camunda.optimize.service.db.reader.DefinitionInstanceReader;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Set;

import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;

@AllArgsConstructor
@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionInstanceReaderES implements DecisionInstanceReader {

  private final DefinitionInstanceReader definitionInstanceReader;

  @Override
  public Set<String> getExistingDecisionDefinitionKeysFromInstances() {
    return definitionInstanceReader.getAllExistingDefinitionKeys(DECISION);
  }

}
