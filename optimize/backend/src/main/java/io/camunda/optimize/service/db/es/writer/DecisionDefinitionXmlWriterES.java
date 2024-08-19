/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.writer.DecisionDefinitionXmlWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionDefinitionXmlWriterES implements DecisionDefinitionXmlWriter {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(DecisionDefinitionXmlWriterES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public DecisionDefinitionXmlWriterES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void importDecisionDefinitionXmls(
      final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    final String importItemName = "decision definition XML information";
    log.debug("Writing [{}] {} to ES.", decisionDefinitions.size(), importItemName);
    esClient.doImportBulkRequestWithList(
        importItemName,
        decisionDefinitions,
        this::addImportDecisionDefinitionXmlRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  private void addImportDecisionDefinitionXmlRequest(
      final BulkRequest bulkRequest, final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script updateScript =
        ElasticsearchWriterUtil.createFieldUpdateScript(
            FIELDS_TO_UPDATE, decisionDefinitionDto, objectMapper);
    final UpdateRequest updateRequest =
        new UpdateRequest()
            .index(DECISION_DEFINITION_INDEX_NAME)
            .id(decisionDefinitionDto.getId())
            .script(updateScript)
            .upsert(objectMapper.convertValue(decisionDefinitionDto, Map.class))
            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }
}
