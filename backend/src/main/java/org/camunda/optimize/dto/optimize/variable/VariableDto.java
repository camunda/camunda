package org.camunda.optimize.dto.optimize.variable;

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
