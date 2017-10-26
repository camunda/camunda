package org.camunda.optimize.rest.report.decorator;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class OffsetResultListDecorator extends AdjustedResultListDecorator {

  public OffsetResultListDecorator(QueryParameterAdjustedResultList decoratedList) {
    super(decoratedList);
  }

  @Override
  public List<ReportDefinitionDto> adjustList() {
    List<ReportDefinitionDto> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey("resultOffset")) {
      String resultOffset = queryParameters.getFirst("resultOffset");
      try {
        int offset = Integer.parseInt(resultOffset);
        resultList = resultList.subList(offset, resultList.size());
      } catch (NumberFormatException ignored) {}
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
