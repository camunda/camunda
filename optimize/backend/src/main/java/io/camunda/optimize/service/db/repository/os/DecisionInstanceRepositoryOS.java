/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.lt;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;

import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.DecisionInstanceRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class DecisionInstanceRepositoryOS implements DecisionInstanceRepository {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DecisionInstanceRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;
  private final DateTimeFormatter dateTimeFormatter;

  public DecisionInstanceRepositoryOS(
      final OptimizeOpenSearchClient osClient,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService,
      final DateTimeFormatter dateTimeFormatter) {
    this.osClient = osClient;
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  public void importDecisionInstances(
      final String importItemName, final List<DecisionInstanceDto> decisionInstanceDtos) {
    osClient.doImportBulkRequestWithList(
        importItemName,
        decisionInstanceDtos,
        this::addImportDecisionInstanceRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @Override
  public void deleteDecisionInstancesByDefinitionKeyAndEvaluationDateOlderThan(
      final String decisionDefinitionKey, final OffsetDateTime evaluationDate) {
    final String deleteItemIdentifier =
        String.format(
            "decision instances with definitionKey %s and evaluationDate past %s",
            decisionDefinitionKey, evaluationDate);
    osClient.deleteByQueryTask(
        deleteItemIdentifier,
        lt(DecisionInstanceIndex.EVALUATION_DATE_TIME, dateTimeFormatter.format(evaluationDate)),
        true,
        aliasForDecisionDefinitionKey(decisionDefinitionKey));
  }

  private BulkOperation addImportDecisionInstanceRequest(
      final DecisionInstanceDto decisionInstanceDto) {
    final String decisionInstanceId = decisionInstanceDto.getDecisionInstanceId();
    final IndexOperation<DecisionInstanceDto> indexOperation =
        new IndexOperation.Builder<DecisionInstanceDto>()
            .index(aliasForDecisionDefinitionKey(decisionInstanceDto.getDecisionDefinitionKey()))
            .id(decisionInstanceId)
            .document(decisionInstanceDto)
            .build();
    return new BulkOperation.Builder().index(indexOperation).build();
  }

  private String aliasForDecisionDefinitionKey(final String decisionDefinitionKey) {
    return indexNameService.getOptimizeIndexAliasForIndex(
        getDecisionInstanceIndexAliasName(decisionDefinitionKey));
  }
}
