package org.camunda.optimize.service.es.report.command.decision.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty;


public class DecisionViewDtoCreator {

  public static DecisionViewDto createDecisionRawDataView() {
    return new DecisionViewDto(DecisionViewOperation.RAW);
  }

   public static DecisionViewDto createCountFrequencyView() {
     DecisionViewDto view = new DecisionViewDto();
     view.setOperation(DecisionViewOperation.COUNT);
     view.setProperty(DecisionViewProperty.FREQUENCY);
    return view;
  }

}
