package org.camunda.optimize.rest.queryparam.adjustment;

import org.camunda.optimize.dto.optimize.query.util.SortableFields;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OffsetResultListDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OrderByModifiedLastResultListDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OriginalResultList;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.QueryParameterAdjustedResultList;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.RestrictResultListSizeDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.ReverseResultListDecorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.List;

public class QueryParamAdjustmentUtil {

  public static <T extends SortableFields> List<T>
  adjustResultListAccordingToQueryParameters(List<T> resultList,
                                             MultivaluedMap<String, String> queryParameters) {
    QueryParameterAdjustedResultList adjustedResultList =
      new RestrictResultListSizeDecorator(
        new OffsetResultListDecorator(
          new ReverseResultListDecorator(
            new OrderByModifiedLastResultListDecorator(
              new OriginalResultList<>(resultList, queryParameters)
            )
          )
        )
      );
    resultList = adjustedResultList.adjustList();
    return resultList;
  }
}
