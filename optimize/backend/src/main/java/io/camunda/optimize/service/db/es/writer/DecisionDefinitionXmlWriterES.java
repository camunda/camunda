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

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateOperationBuilderES;
import io.camunda.optimize.service.db.writer.DecisionDefinitionXmlWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class DecisionDefinitionXmlWriterES implements DecisionDefinitionXmlWriter {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(DecisionDefinitionXmlWriterES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public DecisionDefinitionXmlWriterES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void importDecisionDefinitionXmls(
      final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    final String importItemName = "decision definition XML information";
    LOG.debug("Writing [{}] {} to ES.", decisionDefinitions.size(), importItemName);
    esClient.doImportBulkRequestWithList(
        importItemName,
        decisionDefinitions,
        this::addImportDecisionDefinitionXmlRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private void addImportDecisionDefinitionXmlRequest(
      final BulkRequest.Builder bulkRequestBuilder,
      final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script updateScript =
        ElasticsearchWriterUtil.createFieldUpdateScript(
            FIELDS_TO_UPDATE, decisionDefinitionDto, objectMapper);
    bulkRequestBuilder.operations(
        o ->
            o.update(
                OptimizeUpdateOperationBuilderES.of(
                    u ->
                        u.optimizeIndex(esClient, DECISION_DEFINITION_INDEX_NAME)
                            .id(decisionDefinitionDto.getId())
                            .action(a -> a.script(updateScript).upsert(decisionDefinitionDto))
                            .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT))));
  }
}
