package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.DateDto;
import org.camunda.optimize.dto.optimize.HeatMapQueryDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

/**
 * @author Askar Akhmerov
 */
public class ValidationHelper {

  public static void validate(HeatMapQueryDto dto) throws OptimizeValidationException {
    ValidationHelper.ensureNotEmpty("ProcessDefinitionId", dto.getProcessDefinitionId());
    if (dto.getFilter() != null && dto.getFilter().getDates() != null) {
      for (DateDto date : dto.getFilter().getDates()) {
        ensureNotEmpty("operator", date.getOperator());
        ensureNotEmpty("type", date.getType());
        ensureNotEmpty("value", date.getValue());
      }
    }
  }

  private static void ensureNotEmpty(String fieldName, Object target) {
    if (target == null || target.toString().isEmpty()) {
      throw new OptimizeValidationException(fieldName + "is not allowed to be empty or null");
    }
  }
}
