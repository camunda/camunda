package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import org.camunda.optimize.dto.optimize.query.util.SortableFields;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class OriginalResultList<T extends SortableFields> implements QueryParameterAdjustedResultList {

  private List<T> resultList;
  private MultivaluedMap<String, String> multivaluedMap;

  public OriginalResultList(List<T> resultList, MultivaluedMap<String, String> multivaluedMap) {
    this.resultList = resultList;
    this.multivaluedMap = multivaluedMap;
  }

  @Override
  public List<T> adjustList() {
    return resultList;
  }

  @Override
  public MultivaluedMap<String, String> getQueryParameters() {
    return multivaluedMap;
  }
}
