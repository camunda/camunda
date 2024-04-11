/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.LabelDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.repository.VariableRepository;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class VariableLabelWriter {
  private final VariableRepository variableRepository;
  private final DatabaseClient databaseClient;

  public void createVariableLabelUpsertRequest(
      final DefinitionVariableLabelsDto definitionVariableLabelsDto) {

    final Map<String, Object> params = new HashMap<>();
    params.put("labels", definitionVariableLabelsDto.getLabels());

    final String query =
        """
      def existingLabels = ctx._source.labels;
      for (label in params['labels']) {
         existingLabels.removeIf(existingLabel -> existingLabel.variableName.equals(label.variableName)
                                                  && existingLabel.variableType.equals(label.variableType)
         );
         if(label.variableLabel != null && !label.variableLabel.trim().isEmpty()) {
              existingLabels.add(label);
         }
      }""";

    final ScriptData scriptData = new ScriptData(params, query);

    final List<LabelDto> labelsForIndexCreation =
        definitionVariableLabelsDto.getLabels().stream()
            .filter(label -> StringUtils.isNotBlank(label.getVariableLabel()))
            .collect(Collectors.toList());
    definitionVariableLabelsDto.setLabels(labelsForIndexCreation);
    variableRepository.upsertVariableLabel(
        VARIABLE_LABEL_INDEX_NAME, definitionVariableLabelsDto, scriptData);
  }

  public void deleteVariableLabelsForDefinition(final String processDefinitionKey) {
    log.debug("Deleting variable label document with id [{}].", processDefinitionKey);
    variableRepository.deleteVariablesForDefinition(
        VARIABLE_LABEL_INDEX_NAME, processDefinitionKey);
  }
}
