/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DATA_SOURCE;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.TENANT_ID;

import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import java.util.List;
import java.util.Set;

public interface ProcessDefinitionWriter {

  Set<String> FIELDS_TO_UPDATE =
      Set.of(
          PROCESS_DEFINITION_KEY,
          PROCESS_DEFINITION_VERSION,
          PROCESS_DEFINITION_VERSION_TAG,
          PROCESS_DEFINITION_NAME,
          DATA_SOURCE,
          TENANT_ID);

  void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs);

  void markDefinitionAsDeleted(final String definitionId);

  boolean markRedeployedDefinitionsAsDeleted(
      final List<ProcessDefinitionOptimizeDto> importedDefinitions);

  void markDefinitionKeysAsOnboarded(final Set<String> definitionKeys);
}
