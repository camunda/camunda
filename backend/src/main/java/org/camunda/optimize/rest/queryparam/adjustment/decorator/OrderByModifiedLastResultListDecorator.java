package org.camunda.optimize.rest.queryparam.adjustment.decorator;

import org.camunda.optimize.dto.optimize.query.util.SortableFields;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Comparator;
import java.util.List;

public class OrderByModifiedLastResultListDecorator extends AdjustedResultListDecorator {

  public OrderByModifiedLastResultListDecorator(QueryParameterAdjustedResultList decoratedList) {
    super(decoratedList);
  }

  @Override
  public <T extends SortableFields> List<T> adjustList() {
    List<T> resultList = decoratedList.adjustList();
    MultivaluedMap<String, String> queryParameters = decoratedList.getQueryParameters();
    if (queryParameters.containsKey("orderBy")) {
      String orderBy = queryParameters.getFirst("orderBy");
      if (orderBy.equals("lastModified")) {
        Comparator<SortableFields> lastModifiedComparator =
            Comparator.comparing(SortableFields::getLastModified).reversed();
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
