package org.camunda.optimize.rest.report.decorator;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Comparator;
import java.util.List;

public class OrderByModifiedLastResultListDecorator extends AdjustedResultListDecorator {

  public OrderByModifiedLastResultListDecorator(QueryParameterAdjustedResultList decoratedList) {
    super(decoratedList);
  }

  @Override
  public List<ReportDefinitionDto> adjustList() {
    List<ReportDefinitionDto> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey("orderBy")) {
      String orderBy = queryParameters.getFirst("orderBy");
      if (orderBy.equals("lastModified")) {
        Comparator<ReportDefinitionDto> lastModifiedComparator =
            Comparator.comparing(ReportDefinitionDto::getLastModified).reversed();
        resultList.sort(lastModifiedComparator);
      }
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
