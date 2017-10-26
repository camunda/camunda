package org.camunda.optimize.rest.report.decorator;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public abstract class AdjustedResultListDecorator implements QueryParameterAdjustedResultList {

  protected QueryParameterAdjustedResultList decoratedList;

  public AdjustedResultListDecorator(QueryParameterAdjustedResultList decoratedList) {
    this.decoratedList = decoratedList;
  }

  @Override
  public List<ReportDefinitionDto> adjustList() {
    return decoratedList.adjustList();
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
