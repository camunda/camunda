/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.os.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.writer.DecisionDefinitionXmlWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DecisionDefinitionXmlWriterOS implements DecisionDefinitionXmlWriter {
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;
  private final ObjectMapper objectMapper;

  @Override
  public void importDecisionDefinitionXmls(
      final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    String importItemName = "decision definition XML information";
    log.debug("Writing [{}] {} to OS.", decisionDefinitions.size(), importItemName);
    osClient.doImportBulkRequestWithList(
        importItemName,
        decisionDefinitions,
        this::addImportDecisionDefinitionXmlRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private BulkOperation addImportDecisionDefinitionXmlRequest(
      final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script script =
        OpenSearchWriterUtil.createFieldUpdateScript(
            FIELDS_TO_UPDATE, decisionDefinitionDto, objectMapper);
    return new BulkOperation.Builder()
        .update(
            new UpdateOperation.Builder<DecisionDefinitionOptimizeDto>()
                .index(
                    indexNameService.getOptimizeIndexAliasForIndex(DECISION_DEFINITION_INDEX_NAME))
                .id(decisionDefinitionDto.getId())
                .script(script)
                .upsert(decisionDefinitionDto)
                .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                .build())
        .build();
  }
}
