/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_NAME;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.TENANT_ID;

public interface ProcessDefinitionWriter {

  Set<String> FIELDS_TO_UPDATE = Set.of(
    PROCESS_DEFINITION_KEY,
    PROCESS_DEFINITION_VERSION,
    PROCESS_DEFINITION_VERSION_TAG,
    PROCESS_DEFINITION_NAME,
    DATA_SOURCE,
    TENANT_ID
  );

  void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs);

  void markDefinitionAsDeleted(final String definitionId);

  boolean markRedeployedDefinitionsAsDeleted(final List<ProcessDefinitionOptimizeDto> importedDefinitions);

  void markDefinitionKeysAsOnboarded(final Set<String> definitionKeys);

}
