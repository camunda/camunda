package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;

import java.time.OffsetDateTime;

/**
 * @author Askar Akhmerov
 */
public class AlertUtil {

  public static void mapBasicFields(AlertCreationDto toCreate, AlertDefinitionDto result) {
    result.setCheckInterval(toCreate.getCheckInterval());
    result.setEmail(toCreate.getEmail());
    result.setFixNotification(toCreate.isFixNotification());
    result.setName(toCreate.getName());
    result.setReminder(toCreate.getReminder());
    result.setReportId(toCreate.getReportId());
    result.setThreshold(toCreate.getThreshold());
    result.setThresholdOperator(toCreate.getThresholdOperator());
  }

  public static void updateFromUser(String userId, AlertDefinitionDto result) {
    result.setLastModified(OffsetDateTime.now());
    result.setLastModifier(userId);
  }
}
