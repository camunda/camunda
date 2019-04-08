/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;

/**
 * This class is using the dto from the plugin system,
 * in order to enable to enrich the variable import.
 *
 * Note: This class is still needed, because it implements
 * the optimize dto opposed to the plugin dto.
 */
public class VariableDto extends PluginVariableDto implements OptimizeDto {

}
