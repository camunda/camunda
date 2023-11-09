/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;

public interface VariableLabelWriter {

  void createVariableLabelUpsertRequest(DefinitionVariableLabelsDto definitionVariableLabelsDto);

  void deleteVariableLabelsForDefinition(final String processDefinitionKey);

}
