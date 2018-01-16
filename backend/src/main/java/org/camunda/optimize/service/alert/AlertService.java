package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
@Component
public class AlertService {
  @Autowired
  private AlertReader alertReader;

  @Autowired
  private AlertWriter alertWriter;

  @Autowired
  private TokenService tokenService;

  public List<AlertDefinitionDto> getStoredAlerts() {
    return alertReader.getStoredAlerts();
  }

  public AlertDefinitionDto createAlert(AlertCreationDto toCreate, String token) {
    String userId = tokenService.getTokenIssuer(token);
    return this.createAlertForUser(toCreate, userId);
  }

  public AlertDefinitionDto createAlertForUser(AlertCreationDto toCreate, String userId) {
    return alertWriter.createAlert(newAlert(toCreate,userId));
  }

  private static AlertDefinitionDto newAlert(AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto result = new AlertDefinitionDto();
    result.setCreated(OffsetDateTime.now());
    result.setOwner(userId);
    updateFromUser(userId, result);

    mapBasicFields(toCreate, result);
    return result;
  }

  private static void mapBasicFields(AlertCreationDto toCreate, AlertDefinitionDto result) {
    result.setCheckInterval(toCreate.getCheckInterval());
    result.setEmail(toCreate.getEmail());
    result.setFixNotification(toCreate.isFixNotification());
    result.setName(toCreate.getName());
    result.setReminder(toCreate.getReminder());
    result.setReportId(toCreate.getReportId());
    result.setThreshold(toCreate.getThreshold());
    result.setThresholdOperator(toCreate.getThresholdOperator());
  }

  private static void updateFromUser(String userId, AlertDefinitionDto result) {
    result.setLastModified(OffsetDateTime.now());
    result.setLastModifier(userId);
  }

  public void updateAlert(String alertId, AlertCreationDto toCreate, String token) {
    String userId = tokenService.getTokenIssuer(token);
    this.updateAlertForUser(alertId, toCreate, userId);
  }

  private void updateAlertForUser(String alertId, AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto toUpdate = alertReader.findAlert(alertId);
    updateFromUser(userId, toUpdate);
    mapBasicFields(toCreate, toUpdate);
    alertWriter.updateAlert(toUpdate);
  }

  public void deleteAlert(String alertId) {
    alertWriter.deleteAlert(alertId);
  }
}
