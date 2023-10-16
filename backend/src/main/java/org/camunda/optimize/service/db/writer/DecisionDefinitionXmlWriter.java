/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.*;

public interface DecisionDefinitionXmlWriter {
  Set<String> FIELDS_TO_UPDATE =
    Set.of(DECISION_DEFINITION_XML, INPUT_VARIABLE_NAMES, OUTPUT_VARIABLE_NAMES);

  void importDecisionDefinitionXmls(final List<DecisionDefinitionOptimizeDto> decisionDefinitions);

}
