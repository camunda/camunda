package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableFilterDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class ValidationHelper {

  public static void validate(HeatMapQueryDto dto) throws OptimizeValidationException {
    ensureNotEmpty("query dto", dto);
    ValidationHelper.ensureNotEmpty("ProcessDefinitionId", dto.getProcessDefinitionId());
    if (dto.getFilter() != null && dto.getFilter().getDates() != null) {
      for (DateFilterDto date : dto.getFilter().getDates()) {
        ensureNotEmpty("operator", date.getOperator());
        ensureNotEmpty("type", date.getType());
        ensureNotEmpty("value", date.getValue());
      }
    }
    if (dto.getFilter() != null && dto.getFilter().getVariables() != null) {
      for (VariableFilterDto variable : dto.getFilter().getVariables()) {
        ensureNotEmpty("operator", variable.getOperator());
        ensureNotEmpty("name", variable.getName());
        ensureNotEmpty("type", variable.getType());
        ensureNotEmpty("value", variable.getValues());
      }
    }
    if (dto.getFilter() != null && dto.getFilter().getExecutedFlowNodes() != null) {
      for (ExecutedFlowNodeFilterDto flowNodeFilter : dto.getFilter().getExecutedFlowNodes()) {
        ensureNotEmpty("operator", flowNodeFilter.getOperator());
        ensureNotEmpty("value", flowNodeFilter.getValues());
      }
    }
  }

  public static void validate(BranchAnalysisQueryDto dto) throws OptimizeValidationException {
    ValidationHelper.validate((HeatMapQueryDto) dto);
    ValidationHelper.ensureNotEmpty("gateway activity id", dto.getGateway());
    ValidationHelper.ensureNotEmpty("end activity id", dto.getEnd());
  }

  public static void ensureNotEmpty(String fieldName, Object target) {
    if (target == null || target.toString().isEmpty()) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be empty or null");
    }
  }

  public static void ensureNotEmptyList(String fieldName, List target) {
    if (target == null || target.isEmpty()) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be empty or null");
    }
  }

  public static void ensureGreaterThanZero(int value) {
    if( value <= 0) {
      throw new OptimizeValidationException("Value should be greater than zero, but was " + value + "!" );
    }
  }

  public static void validate(ReportDataDto dataDto) {
    ensureNotNull("report data", dataDto);
    ViewDto viewDto = dataDto.getView();
    ensureNotNull("view", viewDto);
    ensureNotEmpty("view operation", viewDto.getOperation());
    ensureNotEmpty("visualization", dataDto.getVisualization());
    ValidationHelper.ensureNotEmpty("ProcessDefinitionId", dataDto.getProcessDefinitionId());
    if (dataDto.getFilter() != null && dataDto.getFilter().getDates() != null) {
      for (DateFilterDto date : dataDto.getFilter().getDates()) {
        ensureNotEmpty("date filter operator", date.getOperator());
        ensureNotEmpty("date filter type", date.getType());
        ensureNotEmpty("date filter value", date.getValue());
      }
    }
    if (dataDto.getFilter() != null && dataDto.getFilter().getVariables() != null) {
      for (VariableFilterDto variable : dataDto.getFilter().getVariables()) {
        ensureNotEmpty("variable filter operator", variable.getOperator());
        ensureNotEmpty("variable filter name", variable.getName());
        ensureNotEmpty("variable filter type", variable.getType());
        ensureNotEmpty("variable filter value", variable.getValues());
      }
    }
    if (dataDto.getFilter() != null && dataDto.getFilter().getExecutedFlowNodes() != null) {
      for (ExecutedFlowNodeFilterDto flowNodeFilter : dataDto.getFilter().getExecutedFlowNodes()) {
        ensureNotEmpty("flow node filter operator", flowNodeFilter.getOperator());
        ensureNotEmpty("flow node filter value", flowNodeFilter.getValues());
      }
    }
  }

  public static void ensureNotNull(String fieldName, Object object) {
    if (object == null) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be null");
    }
  }

  public static void ensureGreaterThanZero(long value) {
    if( value <= 0) {
      throw new OptimizeValidationException("Value should be greater than zero, but was " + value + "!" );
    }
  }

  public static <T> void ensureNotEmpty(T[] array) {
    if (array == null || array.length == 0) {
      throw new OptimizeValidationException("Array should not be empty!");
    }
  }
}
