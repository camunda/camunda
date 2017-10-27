package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import org.camunda.optimize.dto.optimize.query.util.SortableFields;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;

public class ReverseResultListDecorator extends AdjustedResultListDecorator {

  public ReverseResultListDecorator(QueryParameterAdjustedResultList decoratedList) {
    super(decoratedList);
  }

  @Override
  public <T extends SortableFields> List<T> adjustList() {
    List<T> resultList = decoratedList.adjustList();
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
