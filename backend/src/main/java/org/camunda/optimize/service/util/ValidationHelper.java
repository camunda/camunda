package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

/**
 * @author Askar Akhmerov
 */
public class ValidationHelper {

  public static void validate(BranchAnalysisQueryDto dto) throws OptimizeValidationException {
    ensureNotEmpty("gateway activity id", dto.getGateway());
    ensureNotEmpty("end activity id", dto.getEnd());
    ensureNotEmpty("query dto", dto);
    ValidationHelper.ensureNotEmpty("ProcessDefinitionKey", dto.getProcessDefinitionKey());
    ValidationHelper.ensureNotEmpty("ProcessDefinitionVersion", dto.getProcessDefinitionVersion());
    if (dto.getFilter() != null) {
      for (FilterDto filterDto : dto.getFilter()) {
        if (filterDto instanceof DateFilterDto) {
          DateFilterDto dateFilterDto = (DateFilterDto) filterDto;
          DateFilterDataDto dateFilterData = dateFilterDto.getData();
          ensureNotEmpty("operator", dateFilterData.getOperator());
          ensureNotEmpty("type", dateFilterData.getType());
          ensureNotEmpty("value", dateFilterData.getValue());
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

    boolean idSet = data != null && data.getProcessDefinitionId() != null;

    boolean valid = versionAndKeySet || idSet;
    if (!valid) {
      throw new OptimizeValidationException("either process definition ID or Key + Version have to be provided");
    }
  }

  public static void validate(ReportDataDto dataDto) {
    ensureNotNull("report data", dataDto);
    ViewDto viewDto = dataDto.getView();
    ensureNotNull("view", viewDto);
    ensureNotEmpty("view operation", viewDto.getOperation());
    ensureNotEmpty("visualization", dataDto.getVisualization());
    validateDefinition(dataDto);

    if (dataDto.getFilter() != null) {
      for (FilterDto filterDto : dataDto.getFilter()) {
        if (filterDto instanceof DateFilterDto) {
          DateFilterDto dateFilterDto = (DateFilterDto) filterDto;
          DateFilterDataDto dateFilterData = dateFilterDto.getData();
          ensureNotEmpty("operator", dateFilterData.getOperator());
          ensureNotEmpty("type", dateFilterData.getType());
          ensureNotEmpty("value", dateFilterData.getValue());
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
