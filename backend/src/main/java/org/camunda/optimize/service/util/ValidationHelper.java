package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.startDate.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.VariableFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class ValidationHelper {

  public static void validate(BranchAnalysisQueryDto dto) throws OptimizeValidationException {
    ensureNotEmpty("gateway activity id", dto.getGateway());
    ensureNotEmpty("end activity id", dto.getEnd());
    ensureNotEmpty("query dto", dto);
    ValidationHelper.ensureNotEmpty("ProcessDefinitionKey", dto.getProcessDefinitionKey());
    ValidationHelper.ensureNotEmpty("ProcessDefinitionVersion", dto.getProcessDefinitionVersion());
    validateFilters(dto.getFilter());
  }

  public static void ensureNotEmpty(String fieldName, Object target) {
    if (target == null || target.toString().isEmpty()) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be empty or null");
    }
  }

  public static void ensureGreaterThanZero(int value) {
    if (value <= 0) {
      throw new OptimizeValidationException("Value should be greater than zero, but was " + value + "!");
    }
  }

  public static void validateCombinedReportDefinition(CombinedReportDefinitionDto reportDefinition) {
    if (reportDefinition.getData() == null) {
      OptimizeValidationException ex =
        new OptimizeValidationException("Report data for a combined report is not allowed to be null!");
      throw new ReportEvaluationException(reportDefinition, ex);
    } else if (reportDefinition.getData().getReportIds() == null) {
      OptimizeValidationException ex =
        new OptimizeValidationException("Reports list for a combined report is not allowed to be null!");
      throw new ReportEvaluationException(reportDefinition, ex);
    }
  }

  public static void validateDefinitionData(ReportDataDto data) {

    if (data instanceof SingleReportDataDto) {
      SingleReportDataDto singleReportData = (SingleReportDataDto) data;
      boolean versionAndKeySet = singleReportData.getProcessDefinitionVersion() != null &&
        singleReportData.getProcessDefinitionKey() != null;

      if (!versionAndKeySet) {
        throw new OptimizeValidationException("process definition key and version have to be provided");
      }
    } else if (data == null) {
      throw new OptimizeValidationException("Report data is not allowed to be null!");
    }
  }

  public static void validate(SingleReportDataDto dataDto) {
    ensureNotNull("report data", dataDto);
    ViewDto viewDto = dataDto.getView();
    ensureNotNull("view", viewDto);
    ensureNotEmpty("view operation", viewDto.getOperation());
    ensureNotEmpty("visualization", dataDto.getVisualization());
    ensureNotNull("group by", dataDto.getGroupBy());
    validateDefinitionData(dataDto);
    validateFilters(dataDto.getFilter());
  }

  private static void validateFilters(List<FilterDto> filters) {
    if (filters != null) {
      for (FilterDto filterDto : filters) {
        if (filterDto instanceof StartDateFilterDto) {
          StartDateFilterDto startDateFilterDto = (StartDateFilterDto) filterDto;
          DateFilterDataDto startDateFilterDataDto = startDateFilterDto.getData();

          ensureAtLeastOneNotNull("start date filter ",
              startDateFilterDataDto.getStart(), startDateFilterDataDto.getEnd()
          );
        } else if (filterDto instanceof VariableFilterDto) {
          VariableFilterDto variableFilterDto = (VariableFilterDto) filterDto;
          VariableFilterDataDto variableFilterData = variableFilterDto.getData();
          ensureNotEmpty("data", variableFilterData.getData());
          ensureNotEmpty("name", variableFilterData.getName());
          ensureNotEmpty("type", variableFilterData.getType());
        } else if (filterDto instanceof ExecutedFlowNodeFilterDto) {
          ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = (ExecutedFlowNodeFilterDto) filterDto;
          ExecutedFlowNodeFilterDataDto flowNodeFilterData = executedFlowNodeFilterDto.getData();
          ensureNotEmpty("operator", flowNodeFilterData.getOperator());
          ensureNotEmpty("value", flowNodeFilterData.getValues());
        }
      }
    }
  }

  public static void ensureAtLeastOneNotNull(String fieldName, Object... objects) {
    boolean oneNotNull = Arrays.stream(objects).anyMatch(Objects::nonNull);
    if (!oneNotNull) {
      throw new OptimizeValidationException(fieldName + " at least one sub field not allowed to be empty or null");
    }
  }

  public static void ensureNotNull(String fieldName, Object object) {
    if (object == null) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be null");
    }
  }

  public static void ensureGreaterThanZero(long value) {
    if (value <= 0) {
      throw new OptimizeValidationException("Value should be greater than zero, but was " + value + "!");
    }
  }
}
