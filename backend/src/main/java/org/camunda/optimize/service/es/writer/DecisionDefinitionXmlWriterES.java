/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.db.writer.DecisionDefinitionXmlWriter;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class DecisionDefinitionXmlWriterES implements DecisionDefinitionXmlWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  @Override
  public void importDecisionDefinitionXmls(final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    String importItemName = "decision definition XML information";
    log.debug("Writing [{}] {} to ES.", decisionDefinitions.size(), importItemName);
    ElasticsearchWriterUtil.doImportBulkRequestWithList(
      esClient,
      importItemName,
      decisionDefinitions,
      this::addImportDecisionDefinitionXmlRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  @SuppressWarnings(SuppressionConstants.UNCHECKED_CAST)
  private void addImportDecisionDefinitionXmlRequest(final BulkRequest bulkRequest,
                                                     final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script updateScript = ElasticsearchWriterUtil.createFieldUpdateScript(
      FIELDS_TO_UPDATE,
      decisionDefinitionDto,
      objectMapper
    );
    UpdateRequest updateRequest =
      new UpdateRequest()
        .index(DECISION_DEFINITION_INDEX_NAME)
        .id(decisionDefinitionDto.getId())
        .script(updateScript)
        .upsert(objectMapper.convertValue(decisionDefinitionDto, Map.class))
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }
}
