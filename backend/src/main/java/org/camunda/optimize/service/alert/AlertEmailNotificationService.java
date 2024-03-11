/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.alert.AlertNotificationDto;
import org.camunda.optimize.service.email.EmailService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class AlertEmailNotificationService implements AlertNotificationService {

  private final ConfigurationService configurationService;
  private final EmailService emailService;

  @Override
  public void notify(@NonNull final AlertNotificationDto notification) {
    final List<String> recipients = notification.getAlert().getEmails();
    log.info(
        "Sending email of type {} to {} recipients for alert with ID {}",
        notification.getType(),
        recipients.size(),
        notification.getAlert().getId());
    notify(notification.getAlertMessage(), recipients);
  }

  @Override
  public String getNotificationDescription() {
    return "alert email";
  }

  private void notify(String text, final List<String> recipients) {
    // This only works as the link is at the end of the composed text. We would need to refactor
    // this if the email
    // structure of alerts changes in future
    String textWithTracking = text + "&utm_medium=email";
    recipients.forEach(
        recipient ->
            emailService.sendEmailWithErrorHandling(
                recipient,
                textWithTracking,
                "["
                    + configurationService.getNotificationEmailCompanyBranding()
                    + "-Optimize] - Report status"));
  }
}
