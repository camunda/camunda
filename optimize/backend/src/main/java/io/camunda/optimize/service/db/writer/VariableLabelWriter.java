/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.VARIABLE_LABEL_INDEX_NAME;

import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.LabelDto;
import io.camunda.optimize.service.db.repository.VariableRepository;
import io.camunda.optimize.service.db.schema.ScriptData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class VariableLabelWriter {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(VariableLabelWriter.class);
  private final VariableRepository variableRepository;

  public VariableLabelWriter(final VariableRepository variableRepository) {
    this.variableRepository = variableRepository;
  }

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
