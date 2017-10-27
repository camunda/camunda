package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import org.camunda.optimize.dto.optimize.query.util.SortableFields;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public abstract class AdjustedResultListDecorator implements QueryParameterAdjustedResultList {

  protected QueryParameterAdjustedResultList decoratedList;

  public AdjustedResultListDecorator(QueryParameterAdjustedResultList decoratedList) {
    this.decoratedList = decoratedList;
  }

  @Override
  public <T extends SortableFields>  List<T> adjustList() {
    return decoratedList.adjustList();
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return decoratedList.getQueryParameters();
  }
}
