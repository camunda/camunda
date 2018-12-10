package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewProperty;


public class DecisionViewDtoCreator {

  public static DecisionViewDto createDecisionRawDataView() {
    return new DecisionViewDto(DecisionViewOperation.RAW);
  }

  public static DecisionViewDto createCountDecisionMatchedRuleFrequencyView() {
    DecisionViewDto view = new DecisionViewDto();
    view.setOperation(DecisionViewOperation.COUNT);
    view.setEntity(DecisionViewEntity.MATCHED_RULE);
    view.setProperty(DecisionViewProperty.FREQUENCY);
    return view;
  }

   public static DecisionViewDto createCountDecisionInstanceFrequencyView() {
     DecisionViewDto view = new DecisionViewDto();
     view.setOperation(DecisionViewOperation.COUNT);
     view.setEntity(DecisionViewEntity.DECISION_INSTANCE);
     view.setProperty(DecisionViewProperty.FREQUENCY);
    return view;
  }

}
