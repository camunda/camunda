/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.ids;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.prefix;
import static org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL.term;
import static org.camunda.optimize.service.db.schema.index.ProcessOverviewIndex.DIGEST;
import static org.camunda.optimize.service.db.schema.index.ProcessOverviewIndex.ENABLED;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import org.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.repository.ProcessRepository;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(OpenSearchCondition.class)
public class ProcessRepositoryOS implements ProcessRepository {
  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  @Override
  public Map<String, ProcessOverviewDto> getProcessOverviewsByKey(
      final Set<String> processDefinitionKeys) {
    log.debug("Fetching process overviews for [{}] processes", processDefinitionKeys.size());
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
    log.debug("Fetching all available process overviews.");

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
    log.debug("Fetching pending process overviews");

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
