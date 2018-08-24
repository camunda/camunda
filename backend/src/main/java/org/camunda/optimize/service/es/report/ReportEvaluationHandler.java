package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedMapReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapSingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_REPORT_TYPE;

@Component
public abstract class ReportEvaluationHandler {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ReportReader reportReader;

  @Autowired
  private ReportEvaluator reportEvaluator;

  public ReportResultDto evaluateSavedReport(String userId, String reportId) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    return evaluateReport(userId, reportDefinition);
  }

  protected ReportResultDto evaluateReport(String userId, ReportDefinitionDto reportDefinition) {
    ReportResultDto result = null;
    if (SINGLE_REPORT_TYPE.equals(reportDefinition.getReportType())) {
      SingleReportDefinitionDto singleReportDefinition =
        (SingleReportDefinitionDto) reportDefinition;
      result = evaluateSingleReport(userId, singleReportDefinition);
    } else if (COMBINED_REPORT_TYPE.equals(reportDefinition.getReportType())) {
      CombinedReportDefinitionDto combinedReportDefinition =
        (CombinedReportDefinitionDto) reportDefinition;
      result = evaluateCombinedReport(userId, combinedReportDefinition);
    } else {
      String message =
        String.format("Can't evaluate report. Unknown report type [%s]", reportDefinition.getReportType());
      logger.warn(message);
    }
    return result;
  }

  public CombinedMapReportResultDto evaluateCombinedReport(String userId,
                                                           CombinedReportDefinitionDto combinedReportDefinition) {

    ValidationHelper.validateCombinedReportDefinition(combinedReportDefinition);
    List<SingleReportResultDto> resultList =
      evaluateListOfReportIds(userId, combinedReportDefinition.getData().getReportIds());
    return transformToCombinedReportResult(combinedReportDefinition, resultList);
  }

  private CombinedMapReportResultDto transformToCombinedReportResult(CombinedReportDefinitionDto combinedReportDefinition,
                                                                     List<SingleReportResultDto> resultList) {
    Map<String, Map<String, Long>> reportIdToMapResult = new HashMap<>();
    resultList
      .stream()
      .filter(r -> r instanceof MapSingleReportResultDto)
      .map(r -> (MapSingleReportResultDto) r)
      .forEach(r -> reportIdToMapResult.put(r.getId(), r.getResult()));

    CombinedMapReportResultDto combinedReportResult =
      new CombinedMapReportResultDto();
    combinedReportResult.setResult(reportIdToMapResult);
    ReportUtil.copyMetaData(combinedReportDefinition, combinedReportResult);
    return combinedReportResult;
  }

  private List<SingleReportResultDto> evaluateListOfReportIds(String userId, List<String> singleReportIds) {
    List<SingleReportResultDto> resultList = new ArrayList<>();
    for (String reportId : singleReportIds) {
      ReportDefinitionDto subReportDefinition = reportReader.getReport(reportId);
      if (SINGLE_REPORT_TYPE.equals(subReportDefinition.getReportType()) ) {

        SingleReportDefinitionDto singleReportDefinition =
          (SingleReportDefinitionDto) subReportDefinition;
        Optional<SingleReportResultDto> singleReportResult =
          evaluateReportForCombinedReport(userId, singleReportDefinition);
        singleReportResult.ifPresent(resultList::add);
      } else {
        String message = "Can't evaluate report. You can only have single reports inside combined reports " +
          "or you are not authorized to evaluate the report!";
        logger.warn(message);
      }
    }
    return resultList;
  }

  private Optional<SingleReportResultDto> evaluateReportForCombinedReport(String userId,
                                                                          SingleReportDefinitionDto reportDefinition) {
    Optional<SingleReportResultDto> result = Optional.empty();
    if (isAuthorizedToSeeReport(userId, reportDefinition)) {
      try {
        SingleReportResultDto singleResult = reportEvaluator.evaluate(reportDefinition.getData());
        ReportUtil.copyMetaData(reportDefinition, singleResult);
        result = Optional.of(singleResult);
      } catch (OptimizeException | OptimizeValidationException ignored) {
        // we just ignore reports that cannot be evaluated in
        // a combined report
      }
    }
    return result;
  }

  /**
   * Checks if the user is allowed to see the given report.
   */
  protected abstract boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto report);

  public SingleReportResultDto evaluateSingleReport(String userId,
                                                    SingleReportDefinitionDto reportDefinition) {
    if (!isAuthorizedToSeeReport(userId, reportDefinition)) {
      SingleReportDataDto reportData = reportDefinition.getData();
      throw new ForbiddenException("User [" + userId + "] is not authorized to evaluate report [" +
        reportDefinition.getName() + "] with process definition [" + reportData.getProcessDefinitionKey() + "].");
    }
    SingleReportResultDto result = evaluateSingleReportWithErrorCheck(reportDefinition);
    ReportUtil.copyMetaData(reportDefinition, result);
    return result;
  }

  private SingleReportResultDto evaluateSingleReportWithErrorCheck(SingleReportDefinitionDto reportDefinition) {
    SingleReportDataDto reportData = reportDefinition.getData();
    try {
      return reportEvaluator.evaluate(reportData);
    } catch (OptimizeException | OptimizeValidationException e) {
      SingleReportDefinitionDto definitionWrapper = new SingleReportDefinitionDto();
      definitionWrapper.setData(reportData);
      definitionWrapper.setName(reportDefinition.getName());
      definitionWrapper.setId(reportDefinition.getId());
      throw new ReportEvaluationException(definitionWrapper, e);
    }
  }

}
