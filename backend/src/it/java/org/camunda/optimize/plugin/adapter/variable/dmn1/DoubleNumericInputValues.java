package org.camunda.optimize.plugin.adapter.variable.dmn1;

import org.camunda.optimize.plugin.importing.variable.DecisionInputImportAdapter;
import org.camunda.optimize.plugin.importing.variable.PluginDecisionInputDto;

import java.util.List;

public class DoubleNumericInputValues implements DecisionInputImportAdapter {

  public List<PluginDecisionInputDto> adaptInputs(List<PluginDecisionInputDto> inputs) {
    for (PluginDecisionInputDto input : inputs) {
      if (input.getType().toLowerCase().equals("double")) {
        input.setValue(String.valueOf(Double.parseDouble(input.getValue()) * 2));
      }
    }
    return inputs;
  }
}
