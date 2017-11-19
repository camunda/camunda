package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.List;

public class ReverseResultListDecorator<T> extends AdjustedResultListDecorator<T> {

  public ReverseResultListDecorator(QueryParameterAdjustedResultList<T> decoratedList) {
    super(decoratedList);
  }

  @Override
  public List<T> adjustList() {
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
