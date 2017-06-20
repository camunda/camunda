package org.camunda.optimize.plugin.adapter.variable.util3;


import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.List;

public class NewVariableAdder implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    PluginVariableDto dto = new PluginVariableDto();
    dto.setName("foo");
    dto.setValue("bar");
    dto.setType("string");
    dto.setId("asdfasdf");
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    list.add(dto);
    return list;
  }
}
