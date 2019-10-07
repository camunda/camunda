/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.BUSINESS_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ENGINE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.START_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.STATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.TENANT_ID;

@Component
@Slf4j
public class RunningProcessInstanceWriter extends AbstractProcessInstanceWriter {
  private static final Set<String> PRIMITIVE_UPDATABLE_FIELDS = ImmutableSet.of(
    PROCESS_DEFINITION_KEY, PROCESS_DEFINITION_VERSION, PROCESS_DEFINITION_ID,
    BUSINESS_KEY, START_DATE, STATE,
    ENGINE, TENANT_ID
  );

  private final OptimizeElasticsearchClient esClient;

  public RunningProcessInstanceWriter(final OptimizeElasticsearchClient esClient,
                                      final ObjectMapper objectMapper) {
    super(objectMapper);
    this.esClient = esClient;
  }

  public void importProcessInstances(List<ProcessInstanceDto> processInstanceDtos) {
    String importItemName = "running process instances";
    log.debug("Writing [{}] {} to ES.", processInstanceDtos.size(), importItemName);

    ElasticsearchWriterUtil.doBulkRequestWithList(
      esClient,
      importItemName,
      processInstanceDtos,
      (request, dto) -> addImportProcessInstanceRequest(
        request,
        dto,
        PRIMITIVE_UPDATABLE_FIELDS,
        objectMapper
      )
    );
  }
}