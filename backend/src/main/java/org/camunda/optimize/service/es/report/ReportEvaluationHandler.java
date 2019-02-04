package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.service.es.reader.ReportReader;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.es.report.result.process.CombinedProcessReportResult;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.service.util.ValidationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
public abstract class ReportEvaluationHandler {

  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private ReportReader reportReader;
  private SingleReportEvaluator singleReportEvaluator;
  private CombinedReportEvaluator combinedReportEvaluator;

  @Autowired
  public ReportEvaluationHandler(ReportReader reportReader, SingleReportEvaluator singleReportEvaluator,
                                 CombinedReportEvaluator combinedReportEvaluator) {
    this.reportReader = reportReader;
    this.singleReportEvaluator = singleReportEvaluator;
    this.combinedReportEvaluator = combinedReportEvaluator;
  }

  public ReportResult evaluateSavedReport(String userId, String reportId) {
    ReportDefinitionDto reportDefinition = reportReader.getReport(reportId);
    return evaluateReport(userId, reportDefinition);
  }

  protected ReportResult evaluateReport(String userId, ReportDefinitionDto reportDefinition) {
    final ReportResult result;
    if (!reportDefinition.getCombined()) {
      switch (reportDefinition.getReportType()) {
        case PROCESS:
          SingleProcessReportDefinitionDto processDefinition =
            (SingleProcessReportDefinitionDto) reportDefinition;
          result = evaluateSingleProcessReport(userId, processDefinition);
          break;
        case DECISION:
          SingleDecisionReportDefinitionDto decisionDefinition =
            (SingleDecisionReportDefinitionDto) reportDefinition;
          result = evaluateSingleDecisionReport(userId, decisionDefinition);
          break;
        default:
          throw new IllegalStateException("Unsupported reportType: " + reportDefinition.getReportType());
      }
    } else {
      CombinedReportDefinitionDto combinedReportDefinition =
        (CombinedReportDefinitionDto) reportDefinition;
      result = evaluateCombinedReport(userId, combinedReportDefinition);
    }
    return result;
  }

  private CombinedProcessReportResult evaluateCombinedReport(String userId,
                                                                CombinedReportDefinitionDto combinedReportDefinition) {

    ValidationHelper.validateCombinedReportDefinition(combinedReportDefinition);
    List<ReportResult> resultList = evaluateListOfReportIds(
      userId, combinedReportDefinition.getData().getReportIds()
    );
    return transformToCombinedReportResult(combinedReportDefinition, resultList);
  }

  private CombinedProcessReportResult transformToCombinedReportResult(
    CombinedReportDefinitionDto combinedReportDefinition,
    List<ReportResult> singleReportResultList) {
    final AtomicReference<Class> singleReportType = new AtomicReference<>();
    final Map<String, ProcessReportResultDto> reportIdToMapResult = singleReportResultList
      .stream()
      .map(ReportResult::getResultAsDto)
      .filter(t -> t instanceof ProcessReportNumberResultDto || t instanceof ProcessReportMapResultDto)
      .map(t -> (ProcessReportResultDto) t)
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
    final CombinedProcessReportResultDto<ProcessReportResultDto> combinedProcessReportResultDto =
      new CombinedProcessReportResultDto<>();
    combinedProcessReportResultDto.setResult(reportIdToMapResult);
    CombinedProcessReportResult result = new CombinedProcessReportResult(combinedProcessReportResultDto);
    result.copyMetaData(combinedReportDefinition);
    result.copyReportData(combinedReportDefinition.getData());
    return result;
  }

  private List<ReportResult> evaluateListOfReportIds(final String userId, List<String> singleReportIds) {
    List<SingleProcessReportDefinitionDto> singleReportDefinitions =
      reportReader.getAllSingleProcessReportsForIds(singleReportIds)
        .stream()
        .filter(r -> isAuthorizedToSeeReport(userId, r))
        .collect(Collectors.toList());
    return combinedReportEvaluator.evaluate(singleReportDefinitions);
  }

  /**
   * Checks if the user is allowed to see the given report.
   */
  protected abstract boolean isAuthorizedToSeeReport(String userId, ReportDefinitionDto report);

  private ReportResult evaluateSingleProcessReport(final String userId,
                                                   final SingleProcessReportDefinitionDto reportDefinition) {

    if (!isAuthorizedToSeeReport(userId, reportDefinition)) {
      ProcessReportDataDto reportData = reportDefinition.getData();
      throw new ForbiddenException(
        "User [" + userId + "] is not authorized to evaluate report ["
          + reportDefinition.getName() + "] with process definition [" + reportData.getProcessDefinitionKey() + "]."
      );
    }

    ReportResult result = evaluateSingleReportWithErrorCheck(reportDefinition);
    result.copyMetaData(reportDefinition);

    return result;
  }

  private ReportResult evaluateSingleReportWithErrorCheck(SingleProcessReportDefinitionDto reportDefinition) {
    ProcessReportDataDto reportData = reportDefinition.getData();
    try {
      return singleReportEvaluator.evaluate(reportData);
    } catch (OptimizeException | OptimizeValidationException e) {
      ProcessReportNumberResultDto definitionWrapper = new ProcessReportNumberResultDto();
      definitionWrapper.setData(reportData);
      definitionWrapper.setName(reportDefinition.getName());
      definitionWrapper.setId(reportDefinition.getId());
      throw new ReportEvaluationException(definitionWrapper, e);
    }
  }

  private ReportResult evaluateSingleDecisionReport(final String userId,
                                                       final SingleDecisionReportDefinitionDto reportDefinition) {

    if (!isAuthorizedToSeeReport(userId, reportDefinition)) {
      DecisionReportDataDto reportData = reportDefinition.getData();
      throw new ForbiddenException(
        "User [" + userId + "] is not authorized to evaluate report ["
          + reportDefinition.getName() + "] with decision definition [" + reportData.getDecisionDefinitionKey() + "]."
      );
    }

    ReportResult result = evaluateSingleReportWithErrorCheck(reportDefinition);
    result.copyMetaData(reportDefinition);
    return result;
  }

  private ReportResult evaluateSingleReportWithErrorCheck(SingleDecisionReportDefinitionDto reportDefinition) {
    DecisionReportDataDto reportData = reportDefinition.getData();
    try {
      return singleReportEvaluator.evaluate(reportData);
    } catch (OptimizeException | OptimizeValidationException e) {
      DecisionReportNumberResultDto definitionWrapper = new DecisionReportNumberResultDto();
      definitionWrapper.setData(reportData);
      definitionWrapper.setName(reportDefinition.getName());
      definitionWrapper.setId(reportDefinition.getId());
      throw new ReportEvaluationException(definitionWrapper, e);
    }
  }

}
