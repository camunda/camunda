/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.adapter.variable.util3;


import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.time.OffsetDateTime;
import java.util.List;

public class NewVariableAdder implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    PluginVariableDto dto = new PluginVariableDto();
    dto.setName("foo");
    dto.setValue("bar");
    dto.setType("string");
    dto.setId("asdfasdf");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setVersion(1L);
    dto.setEngineAlias("1");
    list.add(dto);
    return list;
  }
}
