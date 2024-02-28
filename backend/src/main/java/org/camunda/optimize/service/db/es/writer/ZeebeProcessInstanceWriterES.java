/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import org.camunda.optimize.service.db.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ZeebeProcessInstanceWriterES
    extends AbstractProcessInstanceDataWriterES<ProcessInstanceDto>
    implements ZeebeProcessInstanceWriter {

  private final ObjectMapper objectMapper;

  public ZeebeProcessInstanceWriterES(
      final OptimizeElasticsearchClient esClient,
      final ElasticSearchSchemaManager elasticSearchSchemaManager,
      final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ImportRequestDto> generateProcessInstanceImports(
      List<ProcessInstanceDto> processInstances, final String sourceExportIndex) {
    String importItemName = "zeebe process instances";
    log.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);

    createInstanceIndicesIfMissing(processInstances, ProcessInstanceDto::getProcessDefinitionKey);

    return processInstances.stream()
        .map(
            procInst -> {
              final Map<String, Object> params = new HashMap<>();
              params.put(NEW_INSTANCE, procInst);
              params.put(FORMATTER, OPTIMIZE_DATE_FORMAT);
              params.put(SOURCE_EXPORT_INDEX, sourceExportIndex);
              params.put(FLOW_NODE_INSTANCES, procInst.getFlowNodeInstances());
              return ImportRequestDto.builder()
                  .importName(importItemName)
                  .type(RequestType.UPDATE)
                  .id(procInst.getProcessInstanceId())
                  .indexName(getProcessInstanceIndexAliasName(procInst.getProcessDefinitionKey()))
                  .source(procInst)
                  .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                  .scriptData(
                      DatabaseWriterUtil.createScriptData(
                          createProcessInstanceUpdateScript(), params, objectMapper))
                  .build();
            })
        .collect(Collectors.toList());
  }
}
