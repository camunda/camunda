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
import static io.camunda.optimize.service.db.schema.index.AbstractDefinitionIndex.DEFINITION_DELETED;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex.PROCESS_DEFINITION_XML;
import static java.util.stream.Collectors.toSet;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
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

  public ProcessDefinitionReaderES(
      final DefinitionReaderES definitionReader, final OptimizeElasticsearchClient esClient) {
    this.definitionReader = definitionReader;
    this.esClient = esClient;
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
  public DefinitionReader getDefinitionReader() {
    return definitionReader;
  }
}
