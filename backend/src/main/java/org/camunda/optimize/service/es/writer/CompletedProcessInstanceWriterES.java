/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.db.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class CompletedProcessInstanceWriterES extends AbstractProcessInstanceWriterES implements CompletedProcessInstanceWriter {

  public CompletedProcessInstanceWriterES(final OptimizeElasticsearchClient esClient,
                                          final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                          final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager, objectMapper);
  }

  @Override
  public List<ImportRequestDto> generateProcessInstanceImports(List<ProcessInstanceDto> processInstances) {
    final String importItemName = "completed process instances";
    log.debug("Creating imports for {} [{}].", processInstances.size(), importItemName);
    createInstanceIndicesIfMissing(processInstances, ProcessInstanceDto::getProcessDefinitionKey);
    return processInstances.stream()
      .map(key -> ImportRequestDto.builder()
        .importName(importItemName)
        .client(esClient)
        .request(createImportRequestForProcessInstance(key))
        .build())
      .collect(Collectors.toList());
  }

  @Override
  public void deleteByIds(final String definitionKey,
                          final List<String> processInstanceIds) {
    final BulkRequest bulkRequest = new BulkRequest();
    log.debug("Deleting [{}] process instance documents with bulk request.", processInstanceIds.size());
    processInstanceIds.forEach(
      id -> bulkRequest.add(new DeleteRequest(getProcessInstanceIndexAliasName(definitionKey), id))
    );
    ElasticsearchWriterUtil.doBulkRequest(
      esClient,
      bulkRequest,
      getProcessInstanceIndexAliasName(definitionKey),
      false
    );
  }


  @Override
  protected void addImportProcessInstanceRequest(BulkRequest bulkRequest,
                                                 ProcessInstanceDto procInst,
                                                 Set<String> primitiveUpdatableFields,
                                                 ObjectMapper objectMapper) {
    if (procInst.getEndDate() == null) {
      log.warn("End date should not be null for completed process instances!");
    }

    super.addImportProcessInstanceRequest(bulkRequest, procInst, primitiveUpdatableFields, objectMapper);
  }

  private UpdateRequest createImportRequestForProcessInstance(final ProcessInstanceDto processInstanceDto) {
    if (processInstanceDto.getEndDate() == null) {
      log.warn("End date should not be null for completed process instances!");
    }
    return createImportRequestForProcessInstance(processInstanceDto, UPDATABLE_FIELDS);
  }

}
