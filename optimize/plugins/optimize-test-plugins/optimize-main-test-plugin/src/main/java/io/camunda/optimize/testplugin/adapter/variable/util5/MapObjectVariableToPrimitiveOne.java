/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.adapter.variable.util5;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import io.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapObjectVariableToPrimitiveOne implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(final List<PluginVariableDto> list) {
    final List<PluginVariableDto> resultList = new ArrayList<>();
    for (final PluginVariableDto pluginVariableDto : list) {
      if (pluginVariableDto.getType().equalsIgnoreCase("object")
          && pluginVariableDto.getValueInfo().get("objectTypeName").equals("org.camunda.foo.Person")
          && pluginVariableDto
          .getValueInfo()
          .get("serializationDataFormat")
          .equals("application/json")) {
        final ObjectMapper objectMapper = new ObjectMapper();
        try {
          final Map<String, String> jsonVariable =
              objectMapper.readValue(
                  pluginVariableDto.getValue(), new TypeReference<Map<String, String>>() {
                  });

          final PluginVariableDto nameVar = new PluginVariableDto();
          nameVar.setEngineAlias(pluginVariableDto.getEngineAlias());
          nameVar.setId(pluginVariableDto.getId());
          nameVar.setProcessDefinitionId(pluginVariableDto.getProcessDefinitionId());
          nameVar.setProcessDefinitionKey(pluginVariableDto.getProcessDefinitionKey());
          nameVar.setProcessInstanceId(pluginVariableDto.getProcessInstanceId());
          nameVar.setVersion(1L);
          nameVar.setName("personsName");
          nameVar.setType("String");
          nameVar.setValue(jsonVariable.get("name"));
          nameVar.setTimestamp(pluginVariableDto.getTimestamp());
          resultList.add(nameVar);
        } catch (final IOException e) {
          e.printStackTrace();
        }
      } else {
        resultList.add(pluginVariableDto);
      }
    }

    return resultList;
  }
}
