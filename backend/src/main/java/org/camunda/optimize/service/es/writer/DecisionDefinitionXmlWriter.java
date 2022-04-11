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
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.util.SuppressionConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.INPUT_VARIABLE_NAMES;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.OUTPUT_VARIABLE_NAMES;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionXmlWriter {
  private static final Set<String> FIELDS_TO_UPDATE =
    Set.of(DECISION_DEFINITION_XML, INPUT_VARIABLE_NAMES, OUTPUT_VARIABLE_NAMES);

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

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
