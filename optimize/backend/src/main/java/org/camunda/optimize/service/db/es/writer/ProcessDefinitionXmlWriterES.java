/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.FLOW_NODE_DATA;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.USER_TASK_NAMES;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.writer.ProcessDefinitionXmlWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessDefinitionXmlWriterES implements ProcessDefinitionXmlWriter {
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public void importProcessDefinitionXmls(
      List<ProcessDefinitionOptimizeDto> processDefinitionOptimizeDtos) {
    String importItemName = "process definition information";
    log.debug("Writing [{}] {} to ES.", processDefinitionOptimizeDtos.size(), importItemName);
    esClient.doImportBulkRequestWithList(
        importItemName,
        processDefinitionOptimizeDtos,
        this::addImportProcessDefinitionToRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private void addImportProcessDefinitionToRequest(
      final BulkRequest bulkRequest, final ProcessDefinitionOptimizeDto processDefinitionDto) {
    final Script script =
        ElasticsearchWriterUtil.createFieldUpdateScript(
            Set.of(FLOW_NODE_DATA, USER_TASK_NAMES, PROCESS_DEFINITION_XML),
            processDefinitionDto,
            objectMapper);
    final UpdateRequest updateRequest =
        new UpdateRequest()
            .index(PROCESS_DEFINITION_INDEX_NAME)
            .id(processDefinitionDto.getId())
            .script(script)
            .upsert(objectMapper.convertValue(processDefinitionDto, Map.class))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }
}
