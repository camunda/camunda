/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.ScriptLanguage;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.es.schema.index.ProcessOverviewIndexES;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.repository.ProcessOverviewRepository;
import io.camunda.optimize.service.db.repository.script.ProcessOverviewScriptFactory;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  public void updateKpisForProcessDefinitions(final List<ProcessOverviewDto> processOverviewDtos) {
    final BulkRequest bulkRequest =
        processOverviewDtos.isEmpty()
            ? null
            : BulkRequest.of(
                b -> {
                  processOverviewDtos.forEach(
                      processOverviewDto ->
                          b.operations(
                              o ->
                                  o.update(
                                      OptimizeUpdateOperationBuilderES.of(
                                          u ->
                                              u.optimizeIndex(esClient, PROCESS_OVERVIEW_INDEX_NAME)
                                                  .id(processOverviewDto.getProcessDefinitionKey())
                                                  .action(
                                                      a ->
                                                          a.upsert(processOverviewDto)
                                                              .script(
                                                                  ElasticsearchWriterUtil
                                                                      .createDefaultScriptWithPrimitiveParams(
                                                                          ProcessOverviewScriptFactory
                                                                              .createUpdateKpisScript(),
                                                                          Map.of(
                                                                              "lastKpiEvaluationResults",
                                                                              processOverviewDto
                                                                                  .getLastKpiEvaluationResults()))))
                                                  .retryOnConflict(
                                                      NUMBER_OF_RETRIES_ON_CONFLICT)))));
                  return b;
                });

    esClient.doBulkRequest(bulkRequest, new ProcessOverviewIndexES().getIndexName(), false);
  }

  @Override
  public void updateProcessConfiguration(
      final String processDefinitionKey, final ProcessOverviewDto overviewDto) {
    try {
      final Map<String, JsonData> paramMap = new HashMap<>();
      if (overviewDto.getOwner() != null) {
        paramMap.put("owner", JsonData.of(overviewDto.getOwner()));
      }
      paramMap.put("processDefinitionKey", JsonData.of(overviewDto.getProcessDefinitionKey()));
      paramMap.put("digestEnabled", JsonData.of(overviewDto.getDigest().isEnabled()));
      final UpdateRequest<ProcessOverviewDto, ?> updateRequest =
          OptimizeUpdateRequestBuilderES.of(
              u ->
                  u.optimizeIndex(esClient, PROCESS_OVERVIEW_INDEX_NAME)
                      .id(processDefinitionKey)
                      .script(
                          Script.of(
                              i ->
                                  i.lang(ScriptLanguage.Painless)
                                      .source(
                                          ProcessOverviewScriptFactory.createUpdateOverviewScript())
                                      .params(paramMap)))
                      .upsert(overviewDto)
                      .refresh(Refresh.True)
                      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT));
      esClient.update(updateRequest, ProcessOverviewDto.class);
    } catch (final Exception e) {
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
      final UpdateRequest<ProcessDigestDto, ?> updateRequest =
          OptimizeUpdateRequestBuilderES.of(
              u ->
                  u.optimizeIndex(esClient, PROCESS_OVERVIEW_INDEX_NAME)
                      .id(processDefKey)
                      .script(
                          Script.of(
                              i ->
                                  i.lang(ScriptLanguage.Painless)
                                      .source(
                                          ProcessOverviewScriptFactory
                                              .createUpdateProcessDigestScript())
                                      .params(
                                          Map.of(
                                              "kpiReportResults",
                                              JsonData.of(
                                                  processDigestDto.getKpiReportResults())))))
                      .refresh(Refresh.True)
                      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT));
      esClient.update(updateRequest, ProcessDigestDto.class);
    } catch (final Exception e) {
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
      final UpdateRequest<ProcessOverviewDto, ?> updateRequestBuilder =
          OptimizeUpdateRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, PROCESS_OVERVIEW_INDEX_NAME)
                      .id(processDefinitionKey)
                      .script(
                          ElasticsearchWriterUtil.createDefaultScriptWithPrimitiveParams(
                              ProcessOverviewScriptFactory.createUpdateOwnerIfNotSetScript(),
                              Map.of(
                                  "owner", ownerId, "processDefinitionKey", processDefinitionKey)))
                      .upsert(processOverviewDto)
                      .refresh(Refresh.True)
                      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT));
      esClient.update(updateRequestBuilder, ProcessOverviewDto.class);
    } catch (final Exception e) {
      final String errorMessage =
          String.format(
              "There was a problem while updating the owner for process with key: [%s] and owner ID: %s.",
              processDefinitionKey, ownerId);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  @Override
  public void deleteProcessOwnerEntry(final String processDefinitionKey) {
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d ->
                  d.optimizeIndex(esClient, PROCESS_OVERVIEW_INDEX_NAME).id(processDefinitionKey)));
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "There was a problem while deleting process owner entry for %s",
              processDefinitionKey);
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }
}
