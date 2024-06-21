/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.testplugin.adapter.variable.util7;

import io.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import io.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InvalidDateFormatVariableReplacer implements VariableImportAdapter {

  private static final List<String> INVALID_DATE_FORMATS =
      Arrays.asList(
          "10/12/2020",
          "10-11-2020",
          "2020-07-01",
          "10/12/2020+03:00",
          "2020-07-16T10:14:22.761421",
          "2019-06-15T12:00:00.000+02:00",
          "2019-06-16T12:00:00.000+0200");

  @Override
  public List<PluginVariableDto> adaptVariables(final List<PluginVariableDto> list) {
    final List<PluginVariableDto> adaptedVariables = new ArrayList<>();
    for (final PluginVariableDto variable : list) {
      variable.setValue(INVALID_DATE_FORMATS.get(list.indexOf(variable)));
      variable.setTimestamp(OffsetDateTime.now());
      adaptedVariables.add(variable);
    }
    return adaptedVariables;
  }
}
