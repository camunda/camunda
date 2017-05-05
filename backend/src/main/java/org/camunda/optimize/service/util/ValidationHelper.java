package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.DateFilterDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.VariableFilterDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

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

  public static void ensureGreaterThanZero(int value) {
    if( value <= 0) {
      throw new OptimizeValidationException("Value should be greater than zero, but was " + value + "!" );
    }
  }
}
