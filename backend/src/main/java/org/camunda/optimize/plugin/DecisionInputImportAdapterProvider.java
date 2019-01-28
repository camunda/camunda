package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DecisionInputImportAdapterProvider extends PluginProvider<DecisionInputImportAdapter> {

  @Override
  protected Class<DecisionInputImportAdapter> getPluginClass() {
    return DecisionInputImportAdapter.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getDecisionInputImportPluginBasePackages();
  }
}
