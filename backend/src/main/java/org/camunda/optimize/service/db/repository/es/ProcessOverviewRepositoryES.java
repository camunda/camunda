/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.index.ProcessOverviewIndexES;
import org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import org.camunda.optimize.service.db.repository.ProcessOverviewRepository;
import org.camunda.optimize.service.db.repository.script.ProcessOverviewScriptFactory;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessOverviewRepositoryES implements ProcessOverviewRepository {
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public void updateProcessConfiguration(
      final String processDefinitionKey, final ProcessOverviewDto overviewDto) {
    try {
      final Map<String, Object> paramMap = new HashMap<>();
      paramMap.put("owner", overviewDto.getOwner());
      paramMap.put("processDefinitionKey", overviewDto.getProcessDefinitionKey());
      paramMap.put("digestEnabled", overviewDto.getDigest().isEnabled());
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(PROCESS_OVERVIEW_INDEX_NAME)
              .id(processDefinitionKey)
              .script(
                  new Script(
                      ScriptType.INLINE,
                      Script.DEFAULT_SCRIPT_LANG,
                      ProcessOverviewScriptFactory.createUpdateOverviewScript(),
                      paramMap))
              .upsert(objectMapper.convertValue(overviewDto, Map.class))
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      final String errorMessage =
          String.format("There was a problem while updating the process: [%s].", overviewDto);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void updateProcessDigestResults(
      final String processDefKey, final ProcessDigestDto processDigestDto) {
    try {
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(PROCESS_OVERVIEW_INDEX_NAME)
              .id(processDefKey)
              .script(
                  new Script(
                      ScriptType.INLINE,
                      Script.DEFAULT_SCRIPT_LANG,
                      ProcessOverviewScriptFactory.createUpdateProcessDigestScript(),
                      Map.of("kpiReportResults", processDigestDto.getKpiReportResults())))
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      final String errorMessage =
          String.format(
              "There was a problem while updating the digest results for process with key: [%s] and digest results: %s.",
              processDefKey, processDigestDto.getKpiReportResults());
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void updateProcessOwnerIfNotSet(
      final String processDefinitionKey,
      final String ownerId,
      final ProcessOverviewDto processOverviewDto) {
    try {
      final UpdateRequest updateRequest =
          new UpdateRequest()
              .index(PROCESS_OVERVIEW_INDEX_NAME)
              .id(processDefinitionKey)
              .script(
                  ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
                      ProcessOverviewScriptFactory.createUpdateOwnerIfNotSetScript(),
                      Map.of("owner", ownerId, "processDefinitionKey", processDefinitionKey)))
              .upsert(objectMapper.convertValue(processOverviewDto, Map.class))
              .setRefreshPolicy(IMMEDIATE)
              .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
      esClient.update(updateRequest);
    } catch (Exception e) {
      final String errorMessage =
          String.format(
              "There was a problem while updating the owner for process with key: [%s] and owner ID: %s.",
              processDefinitionKey, ownerId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void updateKpisForProcessDefinitions(List<ProcessOverviewDto> processOverviewDtos) {
    final BulkRequest bulkRequest = new BulkRequest();
    processOverviewDtos.forEach(
        processOverviewDto ->
            bulkRequest.add(
                new UpdateRequest()
                    .index(PROCESS_OVERVIEW_INDEX_NAME)
                    .id(processOverviewDto.getProcessDefinitionKey())
                    .upsert(objectMapper.convertValue(processOverviewDto, Map.class))
                    .script(
                        ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
                            ProcessOverviewScriptFactory.createUpdateKpisScript(),
                            Map.of(
                                "lastKpiEvaluationResults",
                                processOverviewDto.getLastKpiEvaluationResults())))
                    .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)));

    esClient.doBulkRequest(bulkRequest, new ProcessOverviewIndexES().getIndexName(), false);
  }

  @Override
  public void deleteProcessOwnerEntry(final String processDefinitionKey) {
    try {
      final DeleteRequest deleteRequest =
          new DeleteRequest().index(PROCESS_OVERVIEW_INDEX_NAME).id(processDefinitionKey);
      esClient.delete(deleteRequest);
    } catch (IOException e) {
      final String errorMessage =
          String.format(
              "There was a problem while deleting process owner entry for %s",
              processDefinitionKey);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }
}
