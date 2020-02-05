/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.testplugin.adapter.variable.util4;

import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.time.OffsetDateTime;
import java.util.List;

public class EnrichVariableByInvalidVariables implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(List<PluginVariableDto> list) {
    list.add(null);

    PluginVariableDto dto = new PluginVariableDto();
    list.add(dto);

    // engine alias is missing
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType("String");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias(null);
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    // process definition id is missing
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType("String");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(null);
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    // process definition key is missing
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType("String");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(null);
    list.add(dto);

    // process instance id is missing
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType("String");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(null);
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    // type is missing
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType(null);
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    // version is missing
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType("String");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(null);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    // name is missing
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName(null);
    dto.setValue("Bar");
    dto.setType("String");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    // type is invalid
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType("asgasdfad");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    // id is missing
    dto = new PluginVariableDto();
    dto.setId(null);
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType("String");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(list.get(0).getProcessInstanceId());
    dto.setProcessDefinitionId(list.get(0).getProcessDefinitionId());
    dto.setProcessDefinitionKey(list.get(0).getProcessDefinitionKey());
    list.add(dto);

    return list;
  }
}
