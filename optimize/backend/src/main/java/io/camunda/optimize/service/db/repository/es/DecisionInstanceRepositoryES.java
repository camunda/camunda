/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexOperationBuilderES;
import io.camunda.optimize.service.db.repository.DecisionInstanceRepository;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionInstanceRepositoryES implements DecisionInstanceRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DecisionInstanceRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;
  private final TaskRepositoryES taskRepositoryES;

  public DecisionInstanceRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper,
      final DateTimeFormatter dateTimeFormatter,
      final TaskRepositoryES taskRepositoryES) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
    this.taskRepositoryES = taskRepositoryES;
  }

  @Override
  public void importDecisionInstances(
      final String importItemName, final List<DecisionInstanceDto> decisionInstanceDtos) {
    esClient.doImportBulkRequestWithList(
        importItemName,
        decisionInstanceDtos,
        this::addImportDecisionInstanceRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @Override
  public void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
      final String decisionDefinitionKey, final OffsetDateTime evaluationDate) {
    final Query query =
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.filter(
                            f ->
                                f.range(
                                    r ->
                                        r.field(DecisionInstanceIndex.EVALUATION_DATE_TIME)
                                            .lt(
                                                JsonData.of(
                                                    dateTimeFormatter.format(evaluationDate)))))));

    taskRepositoryES.tryDeleteByQueryRequest(
        query,
        String.format(
            "decision instances with definitionKey %s and evaluationDate past %s",
            decisionDefinitionKey, evaluationDate),
        true,
        getDecisionInstanceIndexAliasName(decisionDefinitionKey));
  }

  private void addImportDecisionInstanceRequest(
      final BulkRequest.Builder bulkRequestBuilder, final DecisionInstanceDto decisionInstanceDto) {
    bulkRequestBuilder.operations(
        BulkOperation.of(
            b ->
                b.index(
                    OptimizeIndexOperationBuilderES.of(
                        ib ->
                            ib.optimizeIndex(
                                    esClient,
                                    getDecisionInstanceIndexAliasName(
                                        decisionInstanceDto.getDecisionDefinitionKey()))
                                .id(decisionInstanceDto.getDecisionInstanceId())
                                .document(decisionInstanceDto)))));
  }
}
