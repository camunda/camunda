/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
    list.add(new PluginVariableDto());

    final PluginVariableDto validVariableInstance = list.get(0);

    // engine alias is missing
    PluginVariableDto engineAliasMissing = createPluginVariableDto(validVariableInstance);
    engineAliasMissing.setEngineAlias(null);
    list.add(engineAliasMissing);

    // process definition id is missing
    PluginVariableDto processDefinitionIdMissing = createPluginVariableDto(validVariableInstance);
    processDefinitionIdMissing.setProcessDefinitionId(null);
    list.add(processDefinitionIdMissing);

    // process definition key is missing
    PluginVariableDto processDefinitionKeyMissing = createPluginVariableDto(validVariableInstance);
    processDefinitionKeyMissing.setProcessDefinitionKey(null);
    list.add(processDefinitionKeyMissing);

    // process instance id is missing
    PluginVariableDto processInstanceIdMissing = createPluginVariableDto(validVariableInstance);
    processInstanceIdMissing.setProcessInstanceId(null);
    list.add(processInstanceIdMissing);

    // type is missing
    PluginVariableDto typeMissing = createPluginVariableDto(validVariableInstance);
    typeMissing.setType(null);
    list.add(typeMissing);

    // type is invalid
    PluginVariableDto typeInvalid = createPluginVariableDto(validVariableInstance);
    typeInvalid.setType("asgasdfad");
    list.add(typeInvalid);

    // version is missing
    PluginVariableDto versionMissing = createPluginVariableDto(validVariableInstance);
    versionMissing.setVersion(null);
    list.add(versionMissing);

    // name is missing
    PluginVariableDto nameMissing = createPluginVariableDto(validVariableInstance);
    nameMissing.setName(null);
    list.add(nameMissing);

    // timestamp is missing
    PluginVariableDto timestampMissing = createPluginVariableDto(validVariableInstance);
    timestampMissing.setTimestamp(null);
    list.add(timestampMissing);

    // id is missing
    PluginVariableDto idMissing = createPluginVariableDto(validVariableInstance);
    idMissing.setId(null);
    list.add(idMissing);

    return list;
  }

  private PluginVariableDto createPluginVariableDto(final PluginVariableDto siblingVariableDto) {
    final PluginVariableDto dto;
    dto = new PluginVariableDto();
    dto.setId("123");
    dto.setName("Foo");
    dto.setValue("Bar");
    dto.setType("String");
    dto.setTimestamp(OffsetDateTime.now());
    dto.setVersion(1L);
    dto.setEngineAlias("camunda-bpm");
    dto.setProcessInstanceId(siblingVariableDto.getProcessInstanceId());
    dto.setProcessDefinitionId(siblingVariableDto.getProcessDefinitionId());
    dto.setProcessDefinitionKey(siblingVariableDto.getProcessDefinitionKey());
    return dto;
  }
}
