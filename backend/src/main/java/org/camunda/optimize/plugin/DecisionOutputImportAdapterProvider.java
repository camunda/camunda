package org.camunda.optimize.plugin;

import org.camunda.optimize.plugin.importing.variable.DecisionOutputImportAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DecisionOutputImportAdapterProvider extends PluginProvider<DecisionOutputImportAdapter> {

  @Override
  protected Class<DecisionOutputImportAdapter> getPluginClass() {
    return DecisionOutputImportAdapter.class;
  }

  @Override
  protected List<String> getBasePackages() {
    return configurationService.getDecisionOutputImportPluginBasePackages();
  }
}
