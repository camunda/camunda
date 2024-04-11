/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.repository.VariableRepository;
import org.camunda.optimize.service.db.repository.script.ProcessInstanceScriptFactory;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class VariableRepositoryES implements VariableRepository {
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public void deleteVariableDataByProcessInstanceIds(
      final String processDefinitionKey, final List<String> processInstanceIds) {
    final BulkRequest bulkRequest =
        new BulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
    processInstanceIds.forEach(
        id ->
            bulkRequest.add(
                new UpdateRequest(getProcessInstanceIndexAliasName(processDefinitionKey), id)
                    .script(new Script(ProcessInstanceScriptFactory.createVariableClearScript()))
                    .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)));
    esClient.doBulkRequest(
        bulkRequest, getProcessInstanceIndexAliasName(processDefinitionKey), false);
  }

  @Override
  public void upsertVariableLabel(
      final String variableLabelIndexName,
      final DefinitionVariableLabelsDto definitionVariableLabelsDto,
      final ScriptData scriptData) {
    final Script updateEntityScript =
        createDefaultScriptWithSpecificDtoParams(
            scriptData.scriptString(), scriptData.params(), objectMapper);
    try {
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(variableLabelIndexName)
              .id(definitionVariableLabelsDto.getDefinitionKey().toLowerCase())
              .upsert(
                  objectMapper.writeValueAsString(definitionVariableLabelsDto), XContentType.JSON)
              .script(updateEntityScript)
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      esClient.update(updateRequest);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Was not able to update the variable labels for the process definition with id: [%s]",
              definitionVariableLabelsDto.getDefinitionKey());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (final ElasticsearchStatusException e) {
      final String errorMessage =
          String.format(
              "Was not able to update the variable labels for the process definition with id: [%s] due to an Elasticsearch"
                  + " exception",
              definitionVariableLabelsDto.getDefinitionKey());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteVariablesForDefinition(
      final String variableLabelIndexName, final String processDefinitionKey) {
    final DeleteRequest request =
        new DeleteRequest(variableLabelIndexName)
            .id(processDefinitionKey)
            .setRefreshPolicy(IMMEDIATE);

    try {
      esClient.delete(request);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Could not delete variable label document with id [%s]. ", processDefinitionKey);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }
}
