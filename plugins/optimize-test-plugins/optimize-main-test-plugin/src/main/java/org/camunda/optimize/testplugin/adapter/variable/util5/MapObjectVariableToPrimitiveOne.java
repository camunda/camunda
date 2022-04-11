/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.adapter.variable.util5;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapObjectVariableToPrimitiveOne implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    List<PluginVariableDto> resultList = new ArrayList<>();
    for (PluginVariableDto pluginVariableDto : list) {
      if (pluginVariableDto.getType().toLowerCase().equals("object") &&
        pluginVariableDto.getValueInfo().get("objectTypeName").equals("org.camunda.foo.Person") &&
        pluginVariableDto.getValueInfo().get("serializationDataFormat").equals("application/json")
      ) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
          Map<String, String> jsonVariable =
            objectMapper.readValue(pluginVariableDto.getValue(), new TypeReference<Map<String, String>>() {
            });

          PluginVariableDto nameVar = new PluginVariableDto();
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
        } catch (IOException e) {
          e.printStackTrace();
        }
      } else {
        resultList.add(pluginVariableDto);
      }
    }

    return resultList;
  }
}
