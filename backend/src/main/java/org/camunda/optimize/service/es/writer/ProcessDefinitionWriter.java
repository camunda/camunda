/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG;
import static org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex.TENANT_ID;

@Component
@Slf4j
public class ProcessDefinitionWriter extends AbstractProcessDefinitionWriter {
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(
    PROCESS_DEFINITION_KEY,
    PROCESS_DEFINITION_VERSION,
    PROCESS_DEFINITION_VERSION_TAG,
    PROCESS_DEFINITION_NAME,
    ENGINE,
    TENANT_ID
  );

  public ProcessDefinitionWriter(final OptimizeElasticsearchClient esClient,
                                 final ObjectMapper objectMapper) {
    super(objectMapper, esClient);
  }

  public void importProcessDefinitions(List<ProcessDefinitionOptimizeDto> procDefs) {
    log.debug("Writing [{}] process definitions to elasticsearch", procDefs.size());
    writeProcessDefinitionInformation(procDefs);
  }

  @Override
  Script createUpdateScript(final ProcessDefinitionOptimizeDto processDefinitionDto) {
    return ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(FIELDS_TO_UPDATE, processDefinitionDto);
  }

  private void writeProcessDefinitionInformation(List<ProcessDefinitionOptimizeDto> procDefs) {
    String importItemName = "process definition information";
    log.debug("Writing [{}] {} to ES.", procDefs.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      procDefs,
      (request, dto) -> addImportProcessDefinitionToRequest(request, dto)
    );
  }
}
