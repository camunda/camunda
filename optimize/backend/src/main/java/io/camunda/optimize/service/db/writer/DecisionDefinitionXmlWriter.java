/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.schema.index.DecisionDefinitionIndex.*;

import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import java.util.List;
import java.util.Set;

public interface DecisionDefinitionXmlWriter {
  Set<String> FIELDS_TO_UPDATE =
      Set.of(DECISION_DEFINITION_XML, INPUT_VARIABLE_NAMES, OUTPUT_VARIABLE_NAMES);

  void importDecisionDefinitionXmls(final List<DecisionDefinitionOptimizeDto> decisionDefinitions);
}
