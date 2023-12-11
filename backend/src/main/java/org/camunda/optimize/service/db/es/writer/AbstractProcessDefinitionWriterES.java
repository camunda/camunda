/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.writer.AbstractProcessDefinitionWriter;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;

import java.util.Map;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;

@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public abstract class AbstractProcessDefinitionWriterES implements AbstractProcessDefinitionWriter<BulkRequest> {

  protected final Logger log = LoggerFactory.getLogger(getClass());
  protected final ObjectMapper objectMapper;
  protected final OptimizeElasticsearchClient esClient;

  abstract Script createUpdateScript(ProcessDefinitionOptimizeDto processDefinitionDtos);

  @Override
  public void addImportProcessDefinitionToRequest(final BulkRequest bulkRequest,
                                                  final ProcessDefinitionOptimizeDto processDefinitionDto) {
    final Script updateScript = createUpdateScript(processDefinitionDto);

    final UpdateRequest updateRequest = new UpdateRequest()
      .index(PROCESS_DEFINITION_INDEX_NAME)
      .id(processDefinitionDto.getId())
      .script(updateScript)
      .upsert(objectMapper.convertValue(processDefinitionDto, Map.class))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }

}
