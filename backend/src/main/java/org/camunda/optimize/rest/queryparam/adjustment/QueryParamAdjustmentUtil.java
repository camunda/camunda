package org.camunda.optimize.rest.queryparam.adjustment;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.user.OptimizeUserDto;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OffsetResultListDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OrderByQueryParamResultListDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.OriginalResultList;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.QueryParameterAdjustedResultList;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.RestrictResultListSizeDecorator;
import org.camunda.optimize.rest.queryparam.adjustment.decorator.ReverseResultListDecorator;

import javax.ws.rs.core.MultivaluedMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryParamAdjustmentUtil {


  private static final String ORDER_BY = "orderBy";
  private static final Map<String, Map <String, Comparator>> reportComparators = new HashMap();

  private static final String LAST_MODIFIED = "lastModified";
  private static final String NAME = "name";
  private static final String ID = "id";

  private static final String DESC = "desc";
  private static final String ASC = "asc";
  private static final String SORT_ORDER = "sortOrder";

  static {
    Map <String, Comparator> desComparators = new HashMap<>();
    desComparators.put(LAST_MODIFIED, Comparator.comparing(ReportDefinitionDto::getLastModified).reversed());
    desComparators.put(NAME, Comparator.comparing(ReportDefinitionDto::getName).reversed());

    Map <String, Comparator> ascComparators = new HashMap<>();
    ascComparators.put(LAST_MODIFIED, Comparator.comparing(ReportDefinitionDto::getLastModified));
    ascComparators.put(NAME, Comparator.comparing(ReportDefinitionDto::getName));

    reportComparators.put(DESC, desComparators);
    reportComparators.put(ASC, ascComparators);
  }

  public static List<OptimizeUserDto> adjustUserResultsToQueryParameters(
      List<OptimizeUserDto> resultList,
      MultivaluedMap<String, String> queryParameters
  ) {
    Comparator<OptimizeUserDto> sorting =
        Comparator.comparing(OptimizeUserDto::getId);

    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, sorting, ID);
  }

  public static List<ReportDefinitionDto> adjustReportResultsToQueryParameters(
      List<ReportDefinitionDto> resultList,
      MultivaluedMap<String, String> queryParameters
  ) {
    Comparator<ReportDefinitionDto> sorting;

    List<String> key = queryParameters.get(ORDER_BY);
    String queryParam = (key == null || key.isEmpty()) ? LAST_MODIFIED : key.get(0);

    key = queryParameters.get(SORT_ORDER);
    String order = (key == null || key.isEmpty()) ? DESC : key.get(0);
    sorting = reportComparators.get(order).get(queryParam);

    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, sorting, queryParam);
  }

  public static List<DashboardDefinitionDto> adjustDashboardResultsToQueryParameters(
      List<DashboardDefinitionDto> resultList,
      MultivaluedMap<String, String> queryParameters
  ) {
    Comparator<DashboardDefinitionDto> sorting =
        Comparator.comparing(DashboardDefinitionDto::getLastModified).reversed();

    return adjustResultListAccordingToQueryParameters(resultList, queryParameters, sorting, "lastModified");
  }

  private static <T> List<T> adjustResultListAccordingToQueryParameters(
      List<T> resultList,
      MultivaluedMap<String, String> queryParameters,
      Comparator<T> comparator,
      String orderField
  ) {
    QueryParameterAdjustedResultList<T> adjustedResultList =
        new RestrictResultListSizeDecorator<>(
            new OffsetResultListDecorator<>(
                new ReverseResultListDecorator<>(
                    new OrderByQueryParamResultListDecorator<>(
                        new OriginalResultList<>(resultList, queryParameters),
                        orderField,
                        comparator
                    )
                )
            )
        );
    resultList = adjustedResultList.adjustList();

    return resultList;
  }

  public static List<String> adjustVariableValuesToQueryParameters(
      List<String> variableValues,
      MultivaluedMap<String, String> queryParameters
  ) {
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
