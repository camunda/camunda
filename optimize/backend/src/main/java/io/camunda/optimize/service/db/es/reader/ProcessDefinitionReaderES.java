/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.TENANT_ID;
import static io.camunda.optimize.service.util.ExceptionUtil.isInstanceIndexNotFoundException;
import static java.util.stream.Collectors.toSet;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.HasAgentInstancesFilterDataDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.filter.HasAgentInstancesQueryFilterES;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessDefinitionReaderES implements ProcessDefinitionReader {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionReaderES.class);
  private final DefinitionReaderES definitionReader;
  private final OptimizeElasticsearchClient esClient;
  private final HasAgentInstancesQueryFilterES hasAgentInstancesQueryFilter;

  public ProcessDefinitionReaderES(
      final DefinitionReaderES definitionReader,
      final OptimizeElasticsearchClient esClient,
      final HasAgentInstancesQueryFilterES hasAgentInstancesQueryFilter) {
    this.definitionReader = definitionReader;
    this.esClient = esClient;
    this.hasAgentInstancesQueryFilter = hasAgentInstancesQueryFilter;
  }

  @Override
  public Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId) {
    final BoolQuery.Builder query = new BoolQuery.Builder();
    query.must(m -> m.matchAll(l -> l));
    query.must(
        t ->
            t.terms(
                l ->
                    l.field(PROCESS_DEFINITION_ID)
                        .terms(tt -> tt.value(List.of(FieldValue.of(definitionId))))));

    return definitionReader.getDefinitions(DefinitionType.PROCESS, query, true).stream()
        .findFirst()
        .map(ProcessDefinitionOptimizeDto.class::cast);
  }

  @Override
  public Set<String> getAllNonOnboardedProcessDefinitionKeys() {
    final String defKeyAgg = "keyAgg";

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, PROCESS_DEFINITION_INDEX_NAME)
                    .query(
                        q ->
                            q.bool(
                                b ->
                                    b.must(
                                            m ->
                                                m.terms(
                                                    t ->
                                                        t.field(ProcessDefinitionIndex.ONBOARDED)
                                                            .terms(
                                                                tt ->
                                                                    tt.value(
                                                                        List.of(
                                                                            FieldValue.of(
                                                                                false))))))
                                        .must(
                                            m ->
                                                m.term(
                                                    t -> t.field(DEFINITION_DELETED).value(false)))
                                        .should(
                                            ss -> ss.exists(e -> e.field(PROCESS_DEFINITION_XML)))))
                    .aggregations(
                        defKeyAgg,
                        Aggregation.of(
                            a ->
                                a.terms(
                                    t -> t.field(ProcessDefinitionIndex.PROCESS_DEFINITION_KEY))))
                    .source(o -> o.fetch(false))
                    .size(MAX_RESPONSE_SIZE_LIMIT));

    final SearchResponse<?> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch non-onboarded process definition keys.";
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }
    final StringTermsAggregate definitionKeyTerms =
        searchResponse.aggregations().get(defKeyAgg).sterms();
    return definitionKeyTerms.buckets().array().stream()
        .map(e -> e.key().stringValue())
        .collect(toSet());
  }

  @Override
  public Set<String> getProcessDefinitionsWithAgentRuns(final List<String> tenantIds) {
    if (tenantIds.isEmpty()) {
      return Set.of();
    }

    final String defKeyAgg = "agentRunProcessDefinitionKeys";
    final BoolQuery.Builder query = new BoolQuery.Builder();
    final List<FieldValue> nonNullTenantIds =
        tenantIds.stream().filter(Objects::nonNull).map(FieldValue::of).toList();
    query.filter(
        f ->
            f.bool(
                b -> {
                  if (tenantIds.contains(null)) {
                    b.should(s -> s.bool(bb -> bb.mustNot(m -> m.exists(e -> e.field(TENANT_ID)))));
                  }
                  if (!nonNullTenantIds.isEmpty()) {
                    b.should(
                        s ->
                            s.terms(
                                t -> t.field(TENANT_ID).terms(tt -> tt.value(nonNullTenantIds))));
                  }
                  return b;
                }));
    hasAgentInstancesQueryFilter.addFilters(
        query, List.of(new HasAgentInstancesFilterDataDto()), FilterContext.builder().build());

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            s ->
                s.optimizeIndex(esClient, PROCESS_INSTANCE_MULTI_ALIAS)
                    .query(q -> q.bool(query.build()))
                    .aggregations(defKeyAgg, a -> a.terms(t -> t.field(PROCESS_DEFINITION_KEY)))
                    .source(o -> o.fetch(false))
                    .size(MAX_RESPONSE_SIZE_LIMIT));

    final SearchResponse<?> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, Object.class);
    } catch (final IOException e) {
      final String reason = "Was not able to fetch process definitions with agent runs.";
      LOG.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    } catch (final ElasticsearchException e) {
      if (isInstanceIndexNotFoundException(DefinitionType.PROCESS, e)) {
        LOG.info(
            "Was not able to retrieve process definitions with agent runs because no process instance indices exist. "
                + "Returning empty set.");
        return Set.of();
      }
      throw e;
    }

    final StringTermsAggregate definitionKeyTerms =
        searchResponse.aggregations().get(defKeyAgg).sterms();
    return definitionKeyTerms.buckets().array().stream()
        .map(e -> e.key().stringValue())
        .collect(toSet());
  }

  @Override
  public DefinitionReader getDefinitionReader() {
    return definitionReader;
  }
}
