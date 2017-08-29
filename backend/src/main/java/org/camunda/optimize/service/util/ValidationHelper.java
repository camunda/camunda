package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.flownode.FlowNodeIdList;
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
      ExecutedFlowNodeFilterDto filterDto = dto.getFilter().getExecutedFlowNodes();
      ensureNotEmpty("andLinkedIds", filterDto.getAndLinkedIds());
      for (FlowNodeIdList flowNodeIdList : filterDto.getAndLinkedIds()) {
        ensureNotEmptyList("orLinkedIds", flowNodeIdList.getOrLinkedIds());
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

  public static <T> void ensureNotEmpty(T[] array) {
    if (array == null || array.length == 0) {
      throw new OptimizeValidationException("Array should not be empty!");
    }
  }
}
