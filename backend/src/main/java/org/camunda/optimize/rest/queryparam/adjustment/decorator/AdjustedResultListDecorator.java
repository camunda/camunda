package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public abstract class AdjustedResultListDecorator<T> implements QueryParameterAdjustedResultList<T> {

  protected QueryParameterAdjustedResultList<T> decoratedList;

  public AdjustedResultListDecorator(QueryParameterAdjustedResultList<T> decoratedList) {
    this.decoratedList = decoratedList;
  }

  @Override
  public List<T> adjustList() {
    return decoratedList.adjustList();
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
