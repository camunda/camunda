/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.adapter.variable.dmn4;

import io.camunda.optimize.plugin.importing.variable.DecisionOutputImportAdapter;
import io.camunda.optimize.plugin.importing.variable.PluginDecisionOutputDto;
import java.util.List;
import java.util.UUID;

public class AddNewOutput implements DecisionOutputImportAdapter {

  @Override
  public List<PluginDecisionOutputDto> adaptOutputs(final List<PluginDecisionOutputDto> outputs) {
    final PluginDecisionOutputDto output = new PluginDecisionOutputDto();
    output.setRuleId(UUID.randomUUID().toString());
    output.setRuleOrder(2);
    output.setVariableName("foo");
    output.setType("Long");
    output.setValue("12000");
    output.setClauseId(UUID.randomUUID().toString());
    output.setClauseName("bar");
    output.setId(UUID.randomUUID().toString());
    output.setDecisionDefinitionId(UUID.randomUUID().toString());
    output.setDecisionDefinitionKey("decide");
    output.setDecisionDefinitionVersion("2");
    output.setEngineAlias("cam-bpm");
    output.setDecisionInstanceId(UUID.randomUUID().toString());
    outputs.add(output);
    return outputs;
  }
}
