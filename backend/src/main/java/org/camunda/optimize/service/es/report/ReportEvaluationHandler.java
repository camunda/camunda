package org.camunda.optimize.service.es.report;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
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
      SingleReportDefinitionDto singleReportDefinition = (SingleReportDefinitionDto) reportDefinition;
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
    List<ReportResultDto> resultList = evaluateListOfReportIds(
      userId, combinedReportDefinition.getData().getReportIds()
    );
    return transformToCombinedReportResult(combinedReportDefinition, resultList);
  }

  private CombinedProcessReportResultDto transformToCombinedReportResult(
    CombinedReportDefinitionDto combinedReportDefinition,
    List<ReportResultDto> singleReportResultList) {
    final AtomicReference<Class> singleReportType = new AtomicReference<>();
    final Map<String, ReportResultDto> reportIdToMapResult = singleReportResultList
      .stream()
      .filter(t -> t instanceof ProcessReportNumberResultDto || t instanceof ProcessReportMapResultDto)
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

  private List<ReportResultDto> evaluateListOfReportIds(String userId, List<String> singleReportIds) {
    List<ReportResultDto> resultList = new ArrayList<>();
    for (String reportId : singleReportIds) {
      ReportDefinitionDto subReportDefinition = reportReader.getReport(reportId);
      if (subReportDefinition instanceof SingleReportDefinitionDto) {
        SingleReportDefinitionDto<?> singleReportDefinition = (SingleReportDefinitionDto) subReportDefinition;
        Optional<ReportResultDto> singleReportResult = evaluateReportForCombinedReport(
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

  private Optional<ReportResultDto> evaluateReportForCombinedReport(String userId,
                                                                    SingleReportDefinitionDto<?> reportDefinition) {
    Optional<ReportResultDto> result = Optional.empty();
    if (isAuthorizedToSeeReport(userId, reportDefinition)) {
      if (reportDefinition.getData() instanceof ProcessReportDataDto) {
        try {
          ReportResultDto singleResult = reportEvaluator.evaluate(reportDefinition.getData());
          ReportUtil.copyMetaData(reportDefinition, (ReportDefinitionDto) singleResult);
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

  public ReportResultDto evaluateSingleReport(final String userId,
                                              final SingleReportDefinitionDto<?> reportDefinition) {

    switch (reportDefinition.getReportType()) {
      case PROCESS:
        final SingleReportDefinitionDto<ProcessReportDataDto> processReportDefinition =
          (SingleReportDefinitionDto<ProcessReportDataDto>) reportDefinition;
        if (!isAuthorizedToSeeReport(userId, processReportDefinition)) {
          ProcessReportDataDto reportData = processReportDefinition.getData();
          throw new ForbiddenException(
            "User [" + userId + "] is not authorized to evaluate report ["
              + processReportDefinition.getName() + "] with process definition [" + reportData.getProcessDefinitionKey() + "]."
          );
        }
        break;
      case DECISION:
        final SingleReportDefinitionDto<DecisionReportDataDto> decisionReportDefinition =
          (SingleReportDefinitionDto<DecisionReportDataDto>) reportDefinition;
        if (!isAuthorizedToSeeReport(userId, decisionReportDefinition)) {
          DecisionReportDataDto reportData = decisionReportDefinition.getData();
          throw new ForbiddenException(
            "User [" + userId + "] is not authorized to evaluate report ["
              + decisionReportDefinition.getName() + "] with decision definition [" + reportData.getDecisionDefinitionKey() + "]."
          );
        }
        break;
      default:
        throw new IllegalStateException("Unsupported reportType: " + reportDefinition.getReportType());
    }

    ReportResultDto result = evaluateSingleReportWithErrorCheck(reportDefinition);

    if (result instanceof ProcessReportResultDto) {
      ReportUtil.copyMetaData(reportDefinition, (ProcessReportResultDto) result);
    } else {
      ReportUtil.copyMetaData(reportDefinition, (DecisionReportResultDto) result);
    }

    return result;
  }

  private ReportResultDto evaluateSingleReportWithErrorCheck(SingleReportDefinitionDto reportDefinition) {
    ReportDataDto reportData = reportDefinition.getData();
    try {
      return reportEvaluator.evaluate(reportData);
    } catch (OptimizeException | OptimizeValidationException e) {
      SingleReportDefinitionDto definitionWrapper = new ProcessReportNumberResultDto();
      definitionWrapper.setData(reportData);
      definitionWrapper.setName(reportDefinition.getName());
      definitionWrapper.setId(reportDefinition.getId());
      throw new ReportEvaluationException(definitionWrapper, e);
    }
  }

}
