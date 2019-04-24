/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_XML;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionXmlWriter {
  private static final Set<String> FIELDS_TO_UPDATE = ImmutableSet.of(DECISION_DEFINITION_XML);

  private RestHighLevelClient esClient;
  private ObjectMapper objectMapper;

  public void importProcessDefinitionXmls(final List<DecisionDefinitionOptimizeDto> decisionDefinitions) {
    log.debug("writing [{}] decision definition XMLs to ES", decisionDefinitions.size());

    final BulkRequest processDefinitionXmlBulkRequest = new BulkRequest();
    for (DecisionDefinitionOptimizeDto decisionDefinition : decisionDefinitions) {
      addImportProcessDefinitionXmlRequest(processDefinitionXmlBulkRequest, decisionDefinition);
    }

    if (processDefinitionXmlBulkRequest.numberOfActions() > 0) {
      try {
        BulkResponse bulkResponse = esClient.bulk(processDefinitionXmlBulkRequest, RequestOptions.DEFAULT);
        if (bulkResponse.hasFailures()) {
          String errorMessage = String.format(
            "There were failures while writing decision definition xml information. " +
              "Received error message: %s",
            bulkResponse.buildFailureMessage()
          );
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        log.error("There were errors while writing decision definition xml information.", e);
      }
    } else {
      log.warn("Cannot import empty list of decision definition xmls.");
    }
  }

  private void addImportProcessDefinitionXmlRequest(final BulkRequest bulkRequest,
                                                    final DecisionDefinitionOptimizeDto decisionDefinitionDto) {
    final Script updateScript = ElasticsearchWriterUtil.createPrimitiveFieldUpdateScript(FIELDS_TO_UPDATE, decisionDefinitionDto);
    UpdateRequest updateRequest =
      new UpdateRequest(
        getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE),
        DECISION_DEFINITION_TYPE,
        decisionDefinitionDto.getId()
      )
        .script(updateScript)
        .upsert(objectMapper.convertValue(decisionDefinitionDto, Map.class))
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

    bulkRequest.add(updateRequest);
  }

}
