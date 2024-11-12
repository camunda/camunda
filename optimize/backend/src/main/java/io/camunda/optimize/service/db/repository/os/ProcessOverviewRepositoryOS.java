/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.script;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.service.db.es.schema.index.ProcessOverviewIndexES;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.ProcessOverviewRepository;
import io.camunda.optimize.service.db.repository.script.ProcessOverviewScriptFactory;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessOverviewRepositoryOS implements ProcessOverviewRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessOverviewRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  public ProcessOverviewRepositoryOS(
      final OptimizeOpenSearchClient osClient, final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
  }

  @Override
  public void updateKpisForProcessDefinitions(final List<ProcessOverviewDto> processOverviewDtos) {
    final List<BulkOperation> bulkOperations =
        processOverviewDtos.stream()
            .map(
                processOverviewDto ->
                    new BulkOperation.Builder()
                        .update(
                            new UpdateOperation.Builder<ProcessOverviewDto>()
                                .index(
                                    indexNameService.getOptimizeIndexAliasForIndex(
                                        PROCESS_OVERVIEW_INDEX_NAME))
                                .id(processOverviewDto.getProcessDefinitionKey())
                                .upsert(processOverviewDto)
                                .script(
                                    script(
                                        ProcessOverviewScriptFactory.createUpdateKpisScript(),
                                        Map.of(
                                            "lastKpiEvaluationResults",
                                            processOverviewDto.getLastKpiEvaluationResults())))
                                .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                                .build())
                        .build())
            .toList();
    osClient.doBulkRequest(
        BulkRequest.Builder::new,
        bulkOperations,
        new ProcessOverviewIndexES().getIndexName(),
        false);
  }

  @Override
  public void updateProcessConfiguration(
      final String processDefinitionKey, final ProcessOverviewDto overviewDto) {
    final Map<String, Object> params =
        new HashMap<>(
            Map.of(
                "processDefinitionKey", overviewDto.getProcessDefinitionKey(),
                "digestEnabled", overviewDto.getDigest().isEnabled()));
    if (overviewDto.getOwner() != null) {
      params.put("owner", overviewDto.getOwner());
    }
    final UpdateRequest.Builder<ProcessOverviewDto, ProcessOverviewDto> requestBuilder =
        new UpdateRequest.Builder<ProcessOverviewDto, ProcessOverviewDto>()
            .index(indexNameService.getOptimizeIndexAliasForIndex(PROCESS_OVERVIEW_INDEX_NAME))
            .id(processDefinitionKey)
            .script(script(ProcessOverviewScriptFactory.createUpdateOverviewScript(), params))
            .upsert(overviewDto)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    osClient.upsert(
        requestBuilder,
        ProcessOverviewDto.class,
        e -> format("There was a problem while updating the process: [%s].", overviewDto));
  }

  @Override
  public void updateProcessDigestResults(
      final String processDefKey, final ProcessDigestDto processDigestDto) {
    final Map<String, Object> params =
        Map.of("kpiReportResults", processDigestDto.getKpiReportResults());
    final UpdateRequest.Builder<Void, ProcessOverviewDto> requestBuilder =
        new UpdateRequest.Builder<Void, ProcessOverviewDto>()
            .index(indexNameService.getOptimizeIndexAliasForIndex(PROCESS_OVERVIEW_INDEX_NAME))
            .id(processDefKey)
            .script(script(ProcessOverviewScriptFactory.createUpdateProcessDigestScript(), params))
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    osClient.update(
        requestBuilder,
        e ->
            format(
                "There was a problem while updating the digest results for process with key: [%s] and digest results: %s.",
                processDefKey, processDigestDto.getKpiReportResults()));
  }

  @Override
  public void updateProcessOwnerIfNotSet(
      final String processDefinitionKey,
      final String ownerId,
      final ProcessOverviewDto processOverviewDto) {
    final Map<String, Object> params =
        Map.of(
            "owner", ownerId,
            "processDefinitionKey", processDefinitionKey);
    final UpdateRequest.Builder<ProcessOverviewDto, ProcessOverviewDto> requestBuilder =
        new UpdateRequest.Builder<ProcessOverviewDto, ProcessOverviewDto>()
            .index(indexNameService.getOptimizeIndexAliasForIndex(PROCESS_OVERVIEW_INDEX_NAME))
            .id(processDefinitionKey)
            .script(script(ProcessOverviewScriptFactory.createUpdateOwnerIfNotSetScript(), params))
            .upsert(processOverviewDto)
            .refresh(Refresh.True)
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    osClient.upsert(
        requestBuilder,
        ProcessOverviewDto.class,
        e ->
            format(
                "There was a problem while updating the owner for process with key: [%s] and owner ID: %s.",
                processDefinitionKey, ownerId));
  }

  @Override
  public void deleteProcessOwnerEntry(final String processDefinitionKey) {
    osClient.delete(
        indexNameService.getOptimizeIndexAliasForIndex(PROCESS_OVERVIEW_INDEX_NAME),
        processDefinitionKey);
  }
}
