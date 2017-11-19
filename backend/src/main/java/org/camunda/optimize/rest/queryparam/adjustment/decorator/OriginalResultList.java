package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class OriginalResultList<T> implements QueryParameterAdjustedResultList<T> {

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
