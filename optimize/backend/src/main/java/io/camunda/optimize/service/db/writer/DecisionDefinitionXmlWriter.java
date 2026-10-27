/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
