/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
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

  public void createVariableLabelUpsertRequest(DefinitionLabelsDto definitionLabelsDto) {
    try {
      final Map<String, Object> params = new HashMap<>();
      params.put("labels", definitionLabelsDto.getLabels());

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

      final Script updateEntityScript = createDefaultScriptWithSpecificDtoParams(
        query,
        params,
        objectMapper
      );

      List<LabelDto> labelsForIndexCreation = definitionLabelsDto.getLabels().stream()
        .filter(label -> StringUtils.isNotBlank(label.getVariableLabel()))
        .collect(Collectors.toList());
      definitionLabelsDto.setLabels(labelsForIndexCreation);

      final UpdateRequest updateRequest = new UpdateRequest()
        .index(VARIABLE_LABEL_INDEX_NAME)
        .id(definitionLabelsDto.getDefinitionKey())
        .upsert(objectMapper.writeValueAsString(definitionLabelsDto), XContentType.JSON)
        .script(updateEntityScript)
        .setRefreshPolicy(IMMEDIATE)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);

      esClient.update(updateRequest);
    } catch (IOException e) {
      String errorMessage = String.format(
        "Was not able to update the variable labels for the process definition with id: [%s]",
        definitionLabelsDto.getDefinitionKey()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    } catch (ElasticsearchStatusException e) {
      String errorMessage = String.format(
        "Was not able to update the variable labels for the process definition with id: [%s] due to an Elasticsearch" +
          " exception",
        definitionLabelsDto.getDefinitionKey()
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

}
