package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Comparator;
import java.util.List;

public class OrderByQueryParamResultListDecorator<T> extends AdjustedResultListDecorator<T> {

  private String queryParamValueToOrderBy;
  private Comparator<T> comparator;

  public OrderByQueryParamResultListDecorator(
      QueryParameterAdjustedResultList<T> decoratedList,
      String queryParamValueToOrderBy,
      Comparator<T> comparator
  ) {
    super(decoratedList);
    this.queryParamValueToOrderBy = queryParamValueToOrderBy;
    this.comparator = comparator;
  }

  @Override
  public List<T> adjustList() {
    List<T> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey("orderBy")) {
      String orderBy = queryParameters.getFirst("orderBy");
      if (orderBy.equals(queryParamValueToOrderBy)) {
        resultList.sort(comparator);
      }
    }
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
