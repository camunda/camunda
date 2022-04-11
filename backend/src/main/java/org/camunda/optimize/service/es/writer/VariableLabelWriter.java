/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_LABEL_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

@RequiredArgsConstructor
@Component
@Slf4j
public class VariableLabelWriter {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public void createVariableLabelUpsertRequest(DefinitionVariableLabelsDto definitionVariableLabelsDto) {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("labels", definitionVariableLabelsDto.getLabels());

      // @formatter:off
      String query =
          "def existingLabels = ctx._source.labels;" +
          "for (label in params['labels']) {" +
          "   existingLabels.removeIf(existingLabel -> existingLabel.variableName.equals(label.variableName) " +
          "                                            && existingLabel.variableType.equals(label.variableType) " +
          "   );" +
          "   if(label.variableLabel != null && !label.variableLabel.trim().isEmpty()) {" +
          "        existingLabels.add(label);" +
          "   }" +
          " }";
      // @formatter:on

      final Script updateEntityScript = createDefaultScriptWithSpecificDtoParams(
        query,
        params,
        objectMapper
      );

      List<LabelDto> labelsForIndexCreation = definitionVariableLabelsDto.getLabels().stream()
        .filter(label -> StringUtils.isNotBlank(label.getVariableLabel()))
        .collect(Collectors.toList());
      definitionVariableLabelsDto.setLabels(labelsForIndexCreation);

      final UpdateRequest updateRequest = new UpdateRequest()
        .index(VARIABLE_LABEL_INDEX_NAME)
        .id(definitionVariableLabelsDto.getDefinitionKey().toLowerCase())
        .upsert(objectMapper.writeValueAsString(definitionVariableLabelsDto), XContentType.JSON)
        .script(updateEntityScript)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      esClient.update(updateRequest);
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update the variable labels for the process definition with id: [%s]",
        definitionVariableLabelsDto.getDefinitionKey()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update the variable labels for the process definition with id: [%s] due to an Elasticsearch" +
          " exception",
        definitionVariableLabelsDto.getDefinitionKey()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  public void deleteVariableLabelsForDefinition(final String processDefinitionKey) {
    log.debug("Deleting variable label document with id [{}].", processDefinitionKey);
    final DeleteRequest request = new DeleteRequest(VARIABLE_LABEL_INDEX_NAME)
      .id(processDefinitionKey)
      .setRefreshPolicy(IMMEDIATE);

    try {
       esClient.delete(request);
    } catch (IOException e) {
      String errorMessage = String.format(
        "Could not delete variable label document with id [%s]. ",
        processDefinitionKey
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

}
