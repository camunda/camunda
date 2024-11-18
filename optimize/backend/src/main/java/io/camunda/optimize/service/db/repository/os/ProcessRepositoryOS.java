/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.ids;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.prefix;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.schema.index.ProcessOverviewIndex.DIGEST;
import static io.camunda.optimize.service.db.schema.index.ProcessOverviewIndex.ENABLED;

import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.ProcessRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessRepositoryOS implements ProcessRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ProcessRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  public ProcessRepositoryOS(
      final OptimizeOpenSearchClient osClient, final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
  }

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsByKey(
      final Set<String> processDefinitionKeys) {
    LOG.debug("Fetching process overviews for [{}] processes", processDefinitionKeys.size());
    if (processDefinitionKeys.isEmpty()) {
      return Collections.emptyMap();
    }

    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(indexNameService.getOptimizeIndexAliasForIndex(PROCESS_OVERVIEW_INDEX_NAME))
            .query(ids(processDefinitionKeys))
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, ProcessOverviewDto.class).stream()
        .collect(
            Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, Function.identity()));
  }

  @Override
  public Map<String, ProcessDigestResponseDto> getAllActiveProcessDigestsByKey() {
    LOG.debug("Fetching all available process overviews.");

    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(indexNameService.getOptimizeIndexAliasForIndex(PROCESS_OVERVIEW_INDEX_NAME))
            .query(term(DIGEST + "." + ENABLED, true))
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, ProcessOverviewDto.class).stream()
        .collect(
            Collectors.toMap(
                ProcessOverviewDto::getProcessDefinitionKey, ProcessOverviewDto::getDigest));
  }

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsWithPendingOwnershipData() {
    LOG.debug("Fetching pending process overviews");

    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(indexNameService.getOptimizeIndexAliasForIndex(PROCESS_OVERVIEW_INDEX_NAME))
            .query(prefix(ProcessOverviewDto.Fields.processDefinitionKey, "pendingauthcheck"))
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, ProcessOverviewDto.class).stream()
        .collect(
            Collectors.toMap(ProcessOverviewDto::getProcessDefinitionKey, Function.identity()));
  }
}
