package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import org.camunda.optimize.dto.optimize.query.util.SortableFields;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class OffsetResultListDecorator extends AdjustedResultListDecorator {

  public OffsetResultListDecorator(QueryParameterAdjustedResultList decoratedList) {
    super(decoratedList);
  }

  @Override
  public <T extends SortableFields> List<T> adjustList() {
    List<T> resultList = decoratedList.adjustList();
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
