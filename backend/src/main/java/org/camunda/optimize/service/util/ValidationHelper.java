package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.CorrelationQueryDto;
import org.camunda.optimize.dto.optimize.DateDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

/**
 * @author Askar Akhmerov
 */
public class ValidationHelper {

  public static void validate(HeatMapQueryDto dto) throws OptimizeValidationException {
    ensureNotEmpty("query dto", dto);
    ValidationHelper.ensureNotEmpty("ProcessDefinitionId", dto.getProcessDefinitionId());
    if (dto.getFilter() != null && dto.getFilter().getDates() != null) {
      for (DateDto date : dto.getFilter().getDates()) {
        ensureNotEmpty("operator", date.getOperator());
        ensureNotEmpty("type", date.getType());
        ensureNotEmpty("value", date.getValue());
      }
    }
  }

  public static void validate(CorrelationQueryDto dto) throws OptimizeValidationException {
    ValidationHelper.validate((HeatMapQueryDto) dto);
    ValidationHelper.ensureNotEmpty("gateway activity id", dto.getGateway());
    ValidationHelper.ensureNotEmpty("end activity id", dto.getEnd());
  }

  public static void ensureNotEmpty(String fieldName, Object target) {
    if (target == null || target.toString().isEmpty()) {
      throw new OptimizeValidationException(fieldName + " is not allowed to be empty or null");
    }
  }
}
