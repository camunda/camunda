package org.camunda.optimize.service.es.filter;

import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class DecisionInputVariableQueryFilter extends DecisionVariableQueryFilter {

  @Autowired
  public DecisionInputVariableQueryFilter(final DateTimeFormatter formatter) {
    super(formatter);
  }

  @Override
  String getVariablePath() {
    return DecisionInstanceType.INPUTS;
  }

}
