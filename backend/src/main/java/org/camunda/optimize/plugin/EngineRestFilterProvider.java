package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.engine.rest.EngineRestFilter;
import org.camunda.optimize.plugin.importing.variable.VariableImportAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EngineRestFilterProvider extends PluginProvider<EngineRestFilter> {

  @Override
  protected Class<EngineRestFilter> getPluginClass() {
    return EngineRestFilter.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getEngineRestFilterPluginBasePackages();
  }

}
