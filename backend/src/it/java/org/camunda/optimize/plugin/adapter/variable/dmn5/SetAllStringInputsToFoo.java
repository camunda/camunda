package org.camunda.optimize.plugin.adapter.variable.dmn5;

import org.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;

import java.util.List;

public class SetAllStringInputsToFoo implements DecisionInputImportAdapter {

  public List<PluginDecisionInputDto> adaptInputs(List<PluginDecisionInputDto> inputs) {
    for (PluginDecisionInputDto input : inputs) {
      if (input.getType().toLowerCase().equals("string")) {
        input.setValue("foo");
      }
    }
    return inputs;
  }
}
