package org.camunda.optimize.rest.report.decorator;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class RestrictResultListSizeDecorator extends AdjustedResultListDecorator {

  public RestrictResultListSizeDecorator(QueryParameterAdjustedResultList decoratedList) {
    super(decoratedList);
  }

  @Override
  public List<ReportDefinitionDto> adjustList() {
    List<ReportDefinitionDto> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey("numResults")) {
      String maxNumberOfResults = queryParameters.getFirst("numResults");
      try {
        int maxNum = Integer.parseInt(maxNumberOfResults);
        maxNum = Math.min(resultList.size(), maxNum);
        resultList = resultList.subList(0, maxNum);
      } catch (NumberFormatException ignored) {}
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
