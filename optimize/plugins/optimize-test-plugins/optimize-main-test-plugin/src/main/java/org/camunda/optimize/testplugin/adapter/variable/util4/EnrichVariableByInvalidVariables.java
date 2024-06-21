/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.adapter.variable.util4;

import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import io.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import java.time.OffsetDateTime;
import java.util.List;

public class EnrichVariableByInvalidVariables implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(final List<PluginVariableDto> list) {
    list.add(null);
    list.add(new PluginVariableDto());

    final PluginVariableDto validVariableInstance = list.get(0);

    // engine alias is missing
    final PluginVariableDto engineAliasMissing = createPluginVariableDto(validVariableInstance);
    engineAliasMissing.setEngineAlias(null);
    list.add(engineAliasMissing);

    // process definition id is missing
    final PluginVariableDto processDefinitionIdMissing = createPluginVariableDto(
        validVariableInstance);
    processDefinitionIdMissing.setProcessDefinitionId(null);
    list.add(processDefinitionIdMissing);

    // process definition key is missing
    final PluginVariableDto processDefinitionKeyMissing = createPluginVariableDto(
        validVariableInstance);
    processDefinitionKeyMissing.setProcessDefinitionKey(null);
    list.add(processDefinitionKeyMissing);

    // process instance id is missing
    final PluginVariableDto processInstanceIdMissing = createPluginVariableDto(
        validVariableInstance);
    processInstanceIdMissing.setProcessInstanceId(null);
    list.add(processInstanceIdMissing);

    // type is missing
    final PluginVariableDto typeMissing = createPluginVariableDto(validVariableInstance);
    typeMissing.setType(null);
    list.add(typeMissing);

    // type is invalid
    final PluginVariableDto typeInvalid = createPluginVariableDto(validVariableInstance);
    typeInvalid.setType("asgasdfad");
    list.add(typeInvalid);

    // version is missing
    final PluginVariableDto versionMissing = createPluginVariableDto(validVariableInstance);
    versionMissing.setVersion(null);
    list.add(versionMissing);

    // name is missing
    final PluginVariableDto nameMissing = createPluginVariableDto(validVariableInstance);
    nameMissing.setName(null);
    list.add(nameMissing);

    // timestamp is missing
    final PluginVariableDto timestampMissing = createPluginVariableDto(validVariableInstance);
    timestampMissing.setTimestamp(null);
    list.add(timestampMissing);

    // id is missing
    final PluginVariableDto idMissing = createPluginVariableDto(validVariableInstance);
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
