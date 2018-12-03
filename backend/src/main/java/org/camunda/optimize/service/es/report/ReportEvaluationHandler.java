package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.MapProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.NumberProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
    final ReportResultDto result;
    if (!reportDefinition.getCombined()) {
      SingleReportDefinitionDto singleReportDefinition =
        (SingleReportDefinitionDto) reportDefinition;
      result = evaluateSingleReport(userId, singleReportDefinition);
    } else {
      CombinedReportDefinitionDto combinedReportDefinition =
        (CombinedReportDefinitionDto) reportDefinition;
      result = evaluateCombinedReport(userId, combinedReportDefinition);
    }
    return result;
  }

  public CombinedProcessReportResultDto evaluateCombinedReport(String userId,
                                                               CombinedReportDefinitionDto combinedReportDefinition) {

    ValidationHelper.validateCombinedReportDefinition(combinedReportDefinition);
    List<ProcessReportResultDto> resultList = evaluateListOfReportIds(
      userId, combinedReportDefinition.getData().getReportIds()
    );
    return transformToCombinedReportResult(combinedReportDefinition, resultList);
  }

  private CombinedProcessReportResultDto transformToCombinedReportResult(
    CombinedReportDefinitionDto combinedReportDefinition,
    List<ProcessReportResultDto> singleReportResultList) {
    final AtomicReference<Class> singleReportType = new AtomicReference<>();
    final Map<String, ProcessReportResultDto> reportIdToMapResult = singleReportResultList
      .stream()
      .filter(t -> t instanceof NumberProcessReportResultDto || t instanceof MapProcessReportResultDto)
      .filter(singleReportResult -> singleReportResult.getClass().equals(singleReportType.get())
        || singleReportType.compareAndSet(null, singleReportResult.getClass()))
      .collect(Collectors.toMap(
        ReportResultDto::getId,
        singleReportResultDto -> singleReportResultDto,
        (u, v) -> {
          throw new IllegalStateException(String.format("Duplicate key %s", u));
        },
        LinkedHashMap::new
      ));
    final CombinedProcessReportResultDto combinedReportResult = new CombinedProcessReportResultDto();
    combinedReportResult.setResult(reportIdToMapResult);
    ReportUtil.copyCombinedReportMetaData(combinedReportDefinition, combinedReportResult);
    return combinedReportResult;
  }

  private List<ProcessReportResultDto> evaluateListOfReportIds(String userId, List<String> singleReportIds) {
    List<ProcessReportResultDto> resultList = new ArrayList<>();
    for (String reportId : singleReportIds) {
      ReportDefinitionDto subReportDefinition = reportReader.getReport(reportId);
      if (subReportDefinition instanceof SingleReportDefinitionDto) {
        SingleReportDefinitionDto<?> singleReportDefinition = (SingleReportDefinitionDto) subReportDefinition;
        Optional<ProcessReportResultDto> singleReportResult = evaluateReportForCombinedReport(
          userId, singleReportDefinition
        );
        singleReportResult.ifPresent(resultList::add);
      } else {
        String message = "Can't evaluate report. You can only have single reports inside combined reports " +
          "or you are not authorized to evaluate the report!";
        logger.warn(message);
      }
    }
    return resultList;
  }

  private Optional<ProcessReportResultDto> evaluateReportForCombinedReport(String userId,
                                                                           SingleReportDefinitionDto<?> reportDefinition) {
    Optional<ProcessReportResultDto> result = Optional.empty();
    if (isAuthorizedToSeeReport(userId, reportDefinition)) {
      if (reportDefinition.getData() instanceof ProcessReportDataDto) {
        try {
          ProcessReportResultDto singleResult =
            reportEvaluator.evaluate((ProcessReportDataDto) reportDefinition.getData());
          ReportUtil.copyMetaData(reportDefinition, singleResult);
          result = Optional.of(singleResult);
        } catch (OptimizeException | OptimizeValidationException ignored) {
          // we just ignore reports that cannot be evaluated in
          // a combined report
        }
      }
    }
    return result;
  }

  /**
   * Checks if the user is allowed to see the given report.
   */
  protected abstract boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto report);

  public ProcessReportResultDto evaluateSingleReport(String userId,
                                                     SingleReportDefinitionDto<ProcessReportDataDto> reportDefinition) {
    if (!isAuthorizedToSeeReport(userId, reportDefinition)) {
      ProcessReportDataDto reportData = reportDefinition.getData();
      throw new ForbiddenException("User [" + userId + "] is not authorized to evaluate report [" +
                                     reportDefinition.getName() + "] with process definition [" + reportData.getProcessDefinitionKey() + "].");
    }
    ProcessReportResultDto result = evaluateSingleReportWithErrorCheck(reportDefinition);
    ReportUtil.copyMetaData(reportDefinition, result);
    return result;
  }

  private ProcessReportResultDto evaluateSingleReportWithErrorCheck(SingleReportDefinitionDto<ProcessReportDataDto> reportDefinition) {
    ProcessReportDataDto reportData = reportDefinition.getData();
    try {
      return reportEvaluator.evaluate(reportData);
    } catch (OptimizeException | OptimizeValidationException e) {
      SingleReportDefinitionDto definitionWrapper = new NumberProcessReportResultDto();
      definitionWrapper.setData(reportData);
      definitionWrapper.setName(reportDefinition.getName());
      definitionWrapper.setId(reportDefinition.getId());
      throw new ReportEvaluationException(definitionWrapper, e);
    }
  }

}
