/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.testplugin.pluginloading.testolddependency;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;

import java.util.Collections;
import java.util.List;

public class OldDependencyUsingTest implements VariableImportAdapter {

  @Override
  public List<PluginVariableDto> adaptVariables(final List<PluginVariableDto> variables) {
    ObjectMapper test = new ObjectMapper();

    final ObjectWriter writer = test.writer();

    /* in this test we are using a deprecated method that got removed in later versions of jackson,
     * which we are using in Optimize.
     * this means that the test will throw an exception on execution if the Optimize version is used !
     */
    final JsonFactory jsonFactory = writer.getJsonFactory();

    return Collections.emptyList();
  }
}
