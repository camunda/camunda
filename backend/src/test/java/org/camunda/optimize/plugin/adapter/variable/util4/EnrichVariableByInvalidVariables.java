package org.camunda.optimize.plugin.adapter.variable.util4;


import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.List;

public class EnrichVariableByInvalidVariables implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    list.add(null);

    PluginVariableDto dto = new PluginVariableDto();
    list.add(dto);

    dto = new PluginVariableDto();
    dto.setType("string");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    dto = new PluginVariableDto();
    dto.setName("foo");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    dto = new PluginVariableDto();
    dto.setName("foo");
    dto.setType("string");
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    dto = new PluginVariableDto();
    dto.setName("foo");
    dto.setType("string");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    dto = new PluginVariableDto();
    dto.setName("foo");
    dto.setType("string");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    list.add(dto);

    dto = new PluginVariableDto();
    dto.setName("foo");
    dto.setType("fasdfasdfdfsa");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    return list;
  }
}
