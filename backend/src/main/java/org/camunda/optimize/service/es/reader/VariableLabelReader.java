/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.VARIABLE_LABEL_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
public class VariableLabelReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(final List<String> processDefinitionKeys) {
    final MultiGetRequest multiGetRequest = new MultiGetRequest();
    processDefinitionKeys.forEach(processDefinitionKey -> multiGetRequest.add(new MultiGetRequest.Item(
      VARIABLE_LABEL_INDEX_NAME,
      processDefinitionKey.toLowerCase()
    )));
    try {
      return Arrays.stream(esClient.mget(multiGetRequest).getResponses())
        .map(this::extractDefinitionLabelsDto)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .peek(label -> label.setDefinitionKey(label.getDefinitionKey().toLowerCase()))
        .collect(Collectors.toMap(DefinitionVariableLabelsDto::getDefinitionKey, Function.identity()));
    } catch (IOException e) {
      final String errorMessage = String.format(
        "There was an error while fetching documents from the variable label index with keys %s.",
        processDefinitionKeys
      );
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private Optional<DefinitionVariableLabelsDto> extractDefinitionLabelsDto(final MultiGetItemResponse multiGetItemResponse) {
    return Optional.ofNullable(multiGetItemResponse.getResponse().getSourceAsString()).map(json -> {
      try {
        return objectMapper.readValue(
          multiGetItemResponse.getResponse().getSourceAsString(),
          DefinitionVariableLabelsDto.class
        );
      } catch (IOException e) {
        throw new OptimizeRuntimeException("Failed parsing response: " + json);
      }
    });
  }

}
