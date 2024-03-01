/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;

import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.os.externalcode.client.dsl.QueryDSL;
import org.camunda.optimize.service.db.reader.DefinitionReader;
import org.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.SourceConfig;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessDefinitionReaderOS implements ProcessDefinitionReader {

  private final DefinitionReaderOS definitionReader;
  private final OptimizeOpenSearchClient osClient;

  @Override
  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId) {
    final BoolQuery query =
        new BoolQuery.Builder()
            .must(QueryDSL.matchAll())
            .must(QueryDSL.term(PROCESS_DEFINITION_ID, definitionId))
            .build();
    return definitionReader.getDefinitions(DefinitionType.PROCESS, query, true).stream()
        .findFirst()
        .map(ProcessDefinitionOptimizeDto.class::cast);
  }

  @Override
  public Set<String> getAllNonOnboardedProcessDefinitionKeys() {
    final String defKeyAgg = "keyAgg";
    final Query query =
        new BoolQuery.Builder()
            .must(QueryDSL.term(ProcessDefinitionIndex.ONBOARDED, false))
            .must(QueryDSL.term(DEFINITION_DELETED, false))
            .should(QueryDSL.exists(PROCESS_DEFINITION_XML))
            .build()
            .toQuery();

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder()
            .index(PROCESS_DEFINITION_INDEX_NAME)
            .size(MAX_RESPONSE_SIZE_LIMIT)
            .query(query)
            .aggregations(
                defKeyAgg,
                new TermsAggregation.Builder()
                    .field(ProcessDefinitionIndex.PROCESS_DEFINITION_KEY)
                    .build()
                    ._toAggregation())
            .source(new SourceConfig.Builder().fetch(false).build());

    final String errorMessage = "Was not able to fetch non-onboarded process definition keys.";
    final SearchResponse<String> searchResponse =
        osClient.search(searchRequest, String.class, errorMessage);
    return OpensearchReaderUtil.extractAggregatedResponseValues(searchResponse, defKeyAgg);
  }

  @Override
  public DefinitionReader getDefinitionReader() {
    return definitionReader;
  }
}
