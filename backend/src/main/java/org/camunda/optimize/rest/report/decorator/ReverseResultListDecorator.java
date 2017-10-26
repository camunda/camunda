package org.camunda.optimize.rest.report.decorator;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;

public class ReverseResultListDecorator extends AdjustedResultListDecorator {

  public ReverseResultListDecorator(QueryParameterAdjustedResultList decoratedList) {
    super(decoratedList);
  }

  @Override
  public List<ReportDefinitionDto> adjustList() {
    List<ReportDefinitionDto> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey("reverseOrder")) {
      String reverseOrder = queryParameters.getFirst("reverseOrder");
      if (reverseOrder.equals("true")) {
        Collections.reverse(resultList);
      }
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
