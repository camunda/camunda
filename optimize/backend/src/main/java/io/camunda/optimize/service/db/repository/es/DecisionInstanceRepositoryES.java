/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil;
import io.camunda.optimize.service.db.repository.DecisionInstanceRepository;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionInstanceRepositoryES implements DecisionInstanceRepository {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DecisionInstanceRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final DateTimeFormatter dateTimeFormatter;

  public DecisionInstanceRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final DateTimeFormatter dateTimeFormatter) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.dateTimeFormatter = dateTimeFormatter;
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
    final BoolQueryBuilder filterQuery =
        boolQuery()
            .filter(
                rangeQuery(DecisionInstanceIndex.EVALUATION_DATE_TIME)
                    .lt(dateTimeFormatter.format(evaluationDate)));

    ElasticsearchWriterUtil.tryDeleteByQueryRequest(
        esClient,
        filterQuery,
        String.format(
            "decision instances with definitionKey %s and evaluationDate past %s",
            decisionDefinitionKey, evaluationDate),
        true,
        getDecisionInstanceIndexAliasName(decisionDefinitionKey));
  }

  private void addImportDecisionInstanceRequest(
      final BulkRequest bulkRequest, final DecisionInstanceDto decisionInstanceDto) {
    final String decisionInstanceId = decisionInstanceDto.getDecisionInstanceId();
    String source = "";
    try {
      source = objectMapper.writeValueAsString(decisionInstanceDto);
    } catch (final JsonProcessingException e) {
      final String reason =
          String.format(
              "Error while processing JSON for decision instance DTO with ID [%s].",
              decisionInstanceDto.getDecisionInstanceId());
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final IndexRequest request =
        new IndexRequest(
                getDecisionInstanceIndexAliasName(decisionInstanceDto.getDecisionDefinitionKey()))
            .id(decisionInstanceId)
            .source(source, XContentType.JSON);

    bulkRequest.add(request);
  }
}
