/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.pluginloading.independent.testotherplugins;

import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import io.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import io.camunda.optimize.testplugin.pluginloading.IndependentNewVariableDto;
import java.util.Collections;
import java.util.List;

public class IndependentTestPlugin implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(final List<PluginVariableDto> variables) {

    final IndependentNewVariableDto newVariableDto = myVeryOwnMethodThatNoOtherPluginHas();
    newVariableDto.anotherNewMethodThatOnlyThisPluginClassHas();
    return Collections.singletonList(newVariableDto);
  }

  public IndependentNewVariableDto myVeryOwnMethodThatNoOtherPluginHas() {
    return new IndependentNewVariableDto();
  }
}
