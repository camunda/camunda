package org.camunda.optimize.rest.queryparam.adjustment;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OffsetResultListDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OrderByQueryParamResultListDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OriginalResultList;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.QueryParameterAdjustedResultList;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.RestrictResultListSizeDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.ReverseResultListDecorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Comparator;
import java.util.List;

public class QueryParamAdjustmentUtil {


  public static List<ReportDefinitionDto> adjustReportResultsToQueryParameters(List<ReportDefinitionDto> resultList,
                                                                               MultivaluedMap<String, String> queryParameters) {
    Comparator<ReportDefinitionDto> lastModifiedComparator =
      Comparator.comparing(ReportDefinitionDto::getLastModified).reversed();
    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, lastModifiedComparator);
  }

  public static List<DashboardDefinitionDto> adjustDashboardResultsToQueryParameters(List<DashboardDefinitionDto> resultList,
                                                                                     MultivaluedMap<String, String> queryParameters) {
    Comparator<DashboardDefinitionDto> lastModifiedComparator =
      Comparator.comparing(DashboardDefinitionDto::getLastModified).reversed();
    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, lastModifiedComparator);
  }

  private static <T> List<T>
  adjustResultListAccordingToQueryParameters(List<T> resultList,
                                             MultivaluedMap<String, String> queryParameters,
                                             Comparator<T> comparator) {
    QueryParameterAdjustedResultList<T> adjustedResultList =
      new RestrictResultListSizeDecorator<>(
        new OffsetResultListDecorator<>(
          new ReverseResultListDecorator<>(
            new OrderByQueryParamResultListDecorator<>(
              new OriginalResultList<>(resultList, queryParameters),
              "lastModified",
              comparator
            )
          )
        )
      );
    resultList = adjustedResultList.adjustList();
    return resultList;
  }

  public static List<String> adjustVariableValuesToQueryParameters(List<String> variableValues,
                                                                   MultivaluedMap<String, String> queryParameters) {
    QueryParameterAdjustedResultList<String> adjustedResultList =
      new RestrictResultListSizeDecorator<>(
        new OffsetResultListDecorator<>(
          new ReverseResultListDecorator<>(
            new OriginalResultList<>(variableValues, queryParameters)
          )
        )
      );
    return adjustedResultList.adjustList();
  }
}
