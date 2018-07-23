package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.filter.*;
import org.camunda.optimize.dto.optimize.query.report.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.startDate.StartDateFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

import java.util.Arrays;
import java.util.List;


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

  public static void validateDefinition(ReportDataDto data) {
    boolean versionAndKeySet = data != null && data.getProcessDefinitionVersion() != null &&
        data.getProcessDefinitionKey() != null;

    if (!versionAndKeySet) {
      throw new OptimizeValidationException("process definition key and version have to be provided");
    }
  }

  public static void validate(ReportDataDto dataDto) {
    ensureNotNull("report data", dataDto);
    ViewDto viewDto = dataDto.getView();
    ensureNotNull("view", viewDto);
    ensureNotEmpty("view operation", viewDto.getOperation());
    ensureNotEmpty("visualization", dataDto.getVisualization());
    ensureNotNull("group by", dataDto.getGroupBy());
    validateDefinition(dataDto);
    validateFilters(dataDto.getFilter());
  }

  private static void validateFilters(List<FilterDto> filters) {
    if (filters != null) {
      for (FilterDto filterDto : filters) {
        if (filterDto instanceof StartDateFilterDto) {
          StartDateFilterDto startDateFilterDto = (StartDateFilterDto) filterDto;
          StartDateFilterDataDto startDateFilterDataDto = startDateFilterDto.getData();

          ensureAtLeastOneNotNull("start date filter ",
              startDateFilterDataDto.getStart(), startDateFilterDataDto.getEnd()
          );
        } else if (filterDto instanceof VariableFilterDto) {
          VariableFilterDto variableFilterDto = (VariableFilterDto) filterDto;
          VariableFilterDataDto variableFilterData = variableFilterDto.getData();
          ensureNotEmpty("operator", variableFilterData.getOperator());
          ensureNotEmpty("name", variableFilterData.getName());
          ensureNotEmpty("type", variableFilterData.getType());
          ensureNotEmpty("value", variableFilterData.getValues());
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
    boolean oneNotNull = Arrays.stream(objects).anyMatch(o -> o != null);
    if (!oneNotNull) {
      throw new OptimizeValidationException(fieldName + " at least one sub field not allowed to be empty or null");
    }
  }

  public static void ensureNotNull(String fieldName, Object object) {
    if (object == null) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be null");
    }
  }

  public static void ensureIsInstanceOf(String fieldName, Object object, Class clazz) {
    ensureNotNull(fieldName, object);
    if (clazz.isInstance(object)) {
      throw new OptimizeValidationException(fieldName + " should be an instance of " + clazz.getSimpleName());
    }
  }

  public static void ensureGreaterThanZero(long value) {
    if (value <= 0) {
      throw new OptimizeValidationException("Value should be greater than zero, but was " + value + "!");
    }
  }
}
